import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api, { ASSET_BASE_URL } from '../api/axios'
import SeatMap from '../components/SeatMap'
import { useAuth } from '../context/AuthContext'
import useEventSocket from '../hooks/useEventSocket'

export default function EventDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()

  const [event, setEvent] = useState(null)
  const [seats, setSeats] = useState([])
  const [selected, setSelected] = useState([]) // array of seat objects this user currently holds a lock on
  const [lockCountdowns, setLockCountdowns] = useState({}) // eventSeatId -> seconds remaining
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [booking, setBooking] = useState(false)
  const [bookingError, setBookingError] = useState('')
  const [lockError, setLockError] = useState('')

  // Kept in a ref so the unmount cleanup effect can release whatever is
  // currently selected without re-subscribing every time selection changes.
  const selectedRef = useRef(selected)
  selectedRef.current = selected

  // While Razorpay's checkout modal is open, the server has already extended
  // the seat lock to the full payment window (see BookingService.createBooking),
  // but this component's local countdown state still only knows about the
  // shorter, original seat-selection TTL. Without this flag, the local
  // countdown could hit zero and clear `selected` mid-payment - visually
  // confusing (the sidebar would show "no seats selected" during an active,
  // still-valid checkout) even though the actual booking would still succeed
  // since confirmBookingAfterPayment re-checks the real server-side lock, not
  // this component's state.
  const checkoutInProgressRef = useRef(false)

  useEffect(() => {
    loadData()
    // On leaving this page (route change, tab close), release any seats this
    // user was holding so they don't sit locked for the full 5-minute TTL
    // just because someone wandered off without booking.
    return () => {
      selectedRef.current.forEach((s) => {
        api.delete(`/events/${id}/seats/${s.id}/lock`).catch(() => { })
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  async function loadData() {
    setLoading(true)
    try {
      const [eventRes, seatsRes] = await Promise.all([
        api.get(`/events/${id}`),
        api.get(`/events/${id}/seats`),
      ])
      setEvent(eventRes.data)
      // Backend returns `eventSeatId` as the bookable identifier; normalize to
      // `id` here so the rest of this component and SeatMap stay simple.
      setSeats(seatsRes.data.map((s) => ({ ...s, id: s.eventSeatId })))
    } catch {
      setError('Could not load this event.')
    } finally {
      setLoading(false)
    }
  }

  // Live seat status pushed from the backend whenever ANY user (including
  // this one, via their own actions) locks, releases, or books a seat.
  const handleSeatUpdate = useCallback((update) => {
    setSeats((prev) =>
      prev.map((s) => (s.id === update.eventSeatId ? { ...s, status: update.status } : s)),
    )

    if (update.status !== 'LOCKED') {
      setLockCountdowns((prev) => {
        const next = { ...prev }
        delete next[update.eventSeatId]
        return next
      })
    }

    // If someone else just booked or the lock on a seat *I* had selected
    // disappeared from under me, drop it from my selection too.
    if (update.status === 'BOOKED') {
      setSelected((prev) => prev.filter((s) => s.id !== update.eventSeatId))
    }
  }, [])

  useEventSocket(id, handleSeatUpdate)

  // Client-side ticking countdown for seats I hold - purely cosmetic (the
  // server-side Redis TTL is the actual source of truth for when a lock
  // expires), but it tells the user "you have this long before it's released".
  // When it reaches zero, we also drop the seat from `selected` here: if
  // nobody else ever interacts with that seat, no WebSocket message will
  // ever arrive to tell us the lock expired (see README's documented
  // "known simplification"), so the UI has to notice on its own instead of
  // freezing on "0:01" forever.
  useEffect(() => {
    const interval = setInterval(() => {
      let expiredSeatIds = []

      setLockCountdowns((prev) => {
        const next = {}
        for (const [seatId, seconds] of Object.entries(prev)) {
          if (seconds > 1) {
            next[seatId] = seconds - 1
          } else {
            expiredSeatIds.push(Number(seatId))
          }
        }
        return next
      })

      if (expiredSeatIds.length > 0 && !checkoutInProgressRef.current) {
        setSelected((prev) => prev.filter((s) => !expiredSeatIds.includes(s.id)))
      }
    }, 1000)
    return () => clearInterval(interval)
  }, [])

  async function toggleSeat(seat) {
    setLockError('')
    const alreadySelected = selected.some((s) => s.id === seat.id)

    if (alreadySelected) {
      setSelected((prev) => prev.filter((s) => s.id !== seat.id))
      try {
        await api.delete(`/events/${id}/seats/${seat.id}/lock`)
      } catch {
        // Non-fatal - the lock will expire on its own via TTL either way
      }
      return
    }

    if (seat.status !== 'AVAILABLE') return

    if (!user) {
      navigate('/login')
      return
    }

    try {
      const { data } = await api.post(`/events/${id}/seats/${seat.id}/lock`)
      if (data.locked) {
        setSelected((prev) => [...prev, seat])
        setLockCountdowns((prev) => ({ ...prev, [seat.id]: data.ttlSeconds }))
      } else {
        // data.message already has the real, specific reason from the
        // backend (paused / cancelled / someone else has it) - showing a
        // hardcoded string here regardless of which one it actually was
        // is exactly the bug this replaces.
        setLockError(data.message || `Seat ${seat.rowLabel}${seat.seatNumber} is not available right now.`)
      }
    } catch (err) {
      setLockError(err.response?.data?.message || 'Could not hold this seat. Please try again.')
    }
  }

  const total = selected.reduce((sum, s) => sum + s.price, 0)

  // Two-step flow: (1) create a PENDING booking + Razorpay order, (2) open
  // Razorpay Checkout, and only actually confirm the booking server-side
  // once /verify-payment validates the payment signature. The booking does
  // NOT exist as "confirmed" between these two steps - closing the checkout
  // modal without paying leaves it PENDING (cleaned up via cancelPendingBooking
  // below, or eventually by the backend's scheduled cleanup job either way).
  async function handleBook() {
    setBooking(true)
    setBookingError('')

    let bookingId
    try {
      const { data } = await api.post('/bookings', {
        eventId: Number(id),
        eventSeatIds: selected.map((s) => s.id),
      })
      bookingId = data.bookingId

      // Free booking (every selected seat priced 0) - the backend already
      // confirmed it server-side (nothing to charge, so no Razorpay order
      // was even created). Nothing to check out - go straight to My Bookings.
      if (!data.paymentRequired) {
        setSelected([])
        navigate('/my-bookings')
        checkoutInProgressRef.current = false
        setBooking(false)
        return
      }

      if (!window.Razorpay) {
        throw new Error('Payment widget failed to load. Check your connection and try again.')
      }

      const checkout = new window.Razorpay({
        key: data.razorpayKeyId,
        order_id: data.razorpayOrderId,
        amount: Math.round(data.amount * 100), // paise, matches what the backend told Razorpay
        currency: data.currency,
        name: 'StubLine',
        description: event.title,
        image: '/stubline-icon.png',
        theme: { color: '#c9a24b' }, // matches the gold accent in the ticket-stub theme
        handler: async (response) => {
          try {
            await api.post('/bookings/verify-payment', {
              bookingId,
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            })
            setSelected([])
            navigate('/my-bookings')
          } catch (err) {
            setBookingError(
              err.response?.data?.message ||
              'Payment succeeded but confirmation failed. Contact support with your payment ID: ' +
              response.razorpay_payment_id,
            )
          } finally {
            checkoutInProgressRef.current = false
            setBooking(false)
          }
        },
        modal: {
          ondismiss: async () => {
            // User closed the checkout widget without paying - release the
            // hold now rather than making everyone else wait out the full
            // payment-window TTL.
            checkoutInProgressRef.current = false
            setBooking(false)
            try {
              await api.post(`/bookings/${bookingId}/cancel-pending`)
            } catch {
              // Best-effort - TTL expiry is the fallback either way
            }
            setSelected([])
            loadData()
          },
        },
      })

      checkoutInProgressRef.current = true
      checkout.open()
      // Deliberately no `finally { setBooking(false) }` here on the success
      // path: `booking` stays true (button stays disabled) for as long as
      // the Razorpay modal is open, so a double-click can't fire a second
      // createBooking() call for the same seats while the first checkout is
      // still in flight. It's reset to false inside the handler and
      // ondismiss callbacks above instead, once the modal actually closes.
    } catch (err) {
      setBookingError(
        err.response?.data?.message || 'Booking failed. One of your seats may have just expired — please pick again.',
      )
      loadData()
      setSelected([])
      setBooking(false)
    }
  }

  if (loading) return <p className="text-paper-muted text-center py-20">Loading…</p>
  if (error) return <p className="text-danger text-center py-20">{error}</p>
  if (!event) return null

  return (
    <div className="max-w-6xl mx-auto px-6 py-12">
      {event.posterImageUrl && (
        <div className="mb-8 rounded-xl overflow-hidden border border-ink-line max-h-80">
          <img
            src={`${ASSET_BASE_URL}${event.posterImageUrl}`}
            alt={`${event.title} poster`}
            className="w-full h-80 object-cover"
          />
        </div>
      )}

      <div className="mb-10">
        <p className="text-gold text-xs tracking-[0.2em] uppercase mb-2">{event.category}</p>
        <h1 className="font-display text-5xl mb-3">{event.title}</h1>
        <p className="text-paper-muted max-w-2xl">{event.description}</p>
        <div className="flex gap-6 mt-4 text-sm font-mono text-paper-muted">
          <span>{new Date(event.eventDate).toLocaleString()}</span>
          <span>{event.venue.name}, {event.venue.city}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Seat map */}
        <div className="lg:col-span-2 card p-6">
          <div className="flex justify-between items-center mb-6">
            <h2 className="font-display text-2xl text-gold">Choose your seats</h2>
            <span className="text-xs text-paper-muted font-mono flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-stub-available animate-pulse" />
              Live
            </span>
          </div>
          {lockError && <p className="text-danger text-xs mb-4">{lockError}</p>}
          <SeatMap
            seats={seats}
            selectedIds={selected.map((s) => s.id)}
            onToggleSeat={toggleSeat}
            lockCountdowns={lockCountdowns}
          />
        </div>

        {/* Ticket-stub booking summary */}
        <div className="lg:col-span-1">
          <div className="sticky top-24 card p-6">
            <h3 className="font-display text-xl text-gold mb-4">Your Selection</h3>

            {selected.length === 0 ? (
              <p className="text-sm text-paper-muted">No seats selected yet.</p>
            ) : (
              <ul className="space-y-2 mb-4">
                {selected
                  .sort((a, b) => a.id - b.id)
                  .map((s) => (
                    <li key={s.id} className="flex justify-between text-sm font-mono">
                      <span>
                        {s.rowLabel}{s.seatNumber} · {s.tier}
                        {lockCountdowns[s.id] != null && (
                          <span className="text-gold ml-2 text-[11px]">
                            ({Math.floor(lockCountdowns[s.id] / 60)}:{String(lockCountdowns[s.id] % 60).padStart(2, '0')})
                          </span>
                        )}
                      </span>
                      <span>₹{s.price}</span>
                    </li>
                  ))}
              </ul>
            )}

            {/* Perforated tear line, ticket-stub signature element */}
            <div className="stub-perforation border-t border-dashed border-ink-line my-4 mx-[-1.5rem]" />

            <div className="flex justify-between items-baseline mb-6">
              <span className="text-paper-muted text-sm">Total</span>
              <span className="font-display text-3xl text-gold">₹{total}</span>
            </div>

            {bookingError && (
              <p className="text-danger text-xs mb-4">{bookingError}</p>
            )}

            <button
              onClick={handleBook}
              disabled={selected.length === 0 || booking}
              className="btn-primary w-full"
            >
              {booking ? 'Booking…' : user ? 'Confirm Booking' : 'Log in to Book'}
            </button>

            <p className="text-[11px] text-paper-muted mt-3 text-center">
              Selecting a seat holds it for you for 5 minutes. Other users will
              see it as unavailable until you confirm or the hold expires.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
