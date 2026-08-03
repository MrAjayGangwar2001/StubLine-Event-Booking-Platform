import { useEffect, useState } from 'react'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import AdminAnalytics from '../components/AdminAnalytics'
import AdminEventsList from '../components/AdminEventsList'
import AdminAnnouncements from '../components/AdminAnnouncements'
import AdminUserManagement from '../components/AdminUserManagement'

function extractErrorMessage(err, fallback) {
  const data = err.response?.data
  if (!data) return fallback
  if (data.message) return data.message
  if (typeof data === 'object') {
    const fieldErrors = Object.entries(data).map(([field, msg]) => `${field}: ${msg}`)
    if (fieldErrors.length > 0) return fieldErrors.join(' · ')
  }
  return fallback
}

export default function Admin() {
  const [tab, setTab] = useState('venue')
  const { isSuperAdmin } = useAuth()

  return (
    <div className={`mx-auto px-6 py-12 ${tab === 'analytics' ? 'max-w-6xl' : 'max-w-3xl'}`}>
      <h1 className="font-display text-5xl mb-2">Admin</h1>
      <p className="text-paper-muted text-sm mb-8">
        Set up a venue and its seat layout, then create an event and put its seats on sale.
      </p>

      <div className="flex gap-2 mb-8 flex-wrap">
        <TabButton active={tab === 'venue'} onClick={() => setTab('venue')}>
          1 · Venue &amp; Seats
        </TabButton>
        <TabButton active={tab === 'event'} onClick={() => setTab('event')}>
          2 · Event &amp; Pricing
        </TabButton>
        <TabButton active={tab === 'analytics'} onClick={() => setTab('analytics')}>
          Analytics
        </TabButton>
        <TabButton active={tab === 'events'} onClick={() => setTab('events')}>
          All Events
        </TabButton>
        <TabButton active={tab === 'announcements'} onClick={() => setTab('announcements')}>
          Announcements
        </TabButton>
        {isSuperAdmin && (
          <TabButton active={tab === 'admins'} onClick={() => setTab('admins')}>
            Manage Admins
          </TabButton>
        )}
      </div>

      {tab === 'venue' && <VenueSetup />}
      {tab === 'event' && <EventSetup />}
      {tab === 'analytics' && <AdminAnalytics />}
      {tab === 'events' && <AdminEventsList />}
      {tab === 'announcements' && <AdminAnnouncements />}
      {tab === 'admins' && isSuperAdmin && <AdminUserManagement />}
    </div>
  )
}

function TabButton({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
        active ? 'bg-gold text-ink' : 'bg-ink-soft text-paper-muted hover:text-paper'
      }`}
    >
      {children}
    </button>
  )
}

/* ---------- Step 1: create a venue, then generate its physical seat layout ---------- */

function VenueSetup() {
  const [venue, setVenue] = useState(null) // set once created, unlocks the seat-layout form
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [city, setCity] = useState('')
  const [totalCapacity, setTotalCapacity] = useState('100')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleCreateVenue(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const { data } = await api.post('/venues', {
        name,
        address,
        city,
        totalCapacity: Number(totalCapacity),
      })
      setVenue(data)
    } catch (err) {
     setError(extractErrorMessage(err, 'Failed to create venue.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (venue) {
    return <SeatLayoutForm venue={venue} onStartOver={() => setVenue(null)} />
  }

  return (
    <form onSubmit={handleCreateVenue} className="card p-6 space-y-4">
      {error && <p className="text-danger text-sm">{error}</p>}

      <div className="grid grid-cols-2 gap-4">
        <Field label="Venue name">
          <input className="input-field" required value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="City">
          <input className="input-field" required value={city} onChange={(e) => setCity(e.target.value)} />
        </Field>
      </div>
      <Field label="Address">
        <input className="input-field" required value={address} onChange={(e) => setAddress(e.target.value)} />
      </Field>
      <Field label="Total capacity">
        <input
          type="number"
          min="1"
          className="input-field"
          required
          value={totalCapacity}
          onChange={(e) => setTotalCapacity(e.target.value)}
        />
      </Field>

      <button type="submit" disabled={submitting} className="btn-primary w-full">
        {submitting ? 'Creating…' : 'Create Venue'}
      </button>
    </form>
  )
}

function SeatLayoutForm({ venue, onStartOver }) {
  const [rows, setRows] = useState([{ rowLabel: 'A', seatCount: 10, tier: 'GOLD' }])
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function updateRow(index, field, value) {
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, [field]: value } : r)))
  }

  function addRow() {
    const nextLabel = String.fromCharCode(65 + rows.length) // A, B, C...
    setRows((prev) => [...prev, { rowLabel: nextLabel, seatCount: 10, tier: 'SILVER' }])
  }

  function removeRow(index) {
    setRows((prev) => prev.filter((_, i) => i !== index))
  }

  const totalSeats = rows.reduce((sum, r) => sum + Number(r.seatCount || 0), 0)

  async function handleSubmit(e) {
    e.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      await api.post(`/venues/${venue.id}/seats/generate`, {
        rows: rows.map((r) => ({ ...r, seatCount: Number(r.seatCount) })),
      })
      setMessage(`${totalSeats} seats generated for "${venue.name}". You can now create an event at this venue.`)
    } catch (err) {
//       setError(err.response?.data?.message || 'Failed to generate seat layout.')
        setError(extractErrorMessage(err, 'Failed to generate seat layout.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="card p-4 flex justify-between items-center">
        <p className="text-sm text-paper-muted">
          Venue created: <span className="text-paper font-medium">{venue.name}</span> ({venue.city})
        </p>
        <button onClick={onStartOver} className="text-xs text-gold hover:underline">
          + New venue instead
        </button>
      </div>

      <form onSubmit={handleSubmit} className="card p-6 space-y-4">
        {message && <p className="text-stub-available text-sm">{message}</p>}
        {error && <p className="text-danger text-sm">{error}</p>}

        <div>
          <p className="text-xs text-paper-muted mb-2">Seat rows (total: {totalSeats})</p>
          <div className="space-y-2">
            {rows.map((row, i) => (
              <div key={i} className="flex gap-2 items-center">
                <input
                  className="input-field !w-20"
                  value={row.rowLabel}
                  onChange={(e) => updateRow(i, 'rowLabel', e.target.value)}
                  placeholder="Row"
                />
                <input
                  type="number"
                  min="1"
                  className="input-field !w-24"
                  value={row.seatCount}
                  onChange={(e) => updateRow(i, 'seatCount', e.target.value)}
                  placeholder="Seats"
                />
                <select
                  className="input-field !w-32"
                  value={row.tier}
                  onChange={(e) => updateRow(i, 'tier', e.target.value)}
                >
                  <option value="GOLD">Gold</option>
                  <option value="SILVER">Silver</option>
                  <option value="PLATINUM">Platinum</option>
                </select>
                {rows.length > 1 && (
                  <button type="button" onClick={() => removeRow(i)} className="text-danger text-xs">
                    Remove
                  </button>
                )}
              </div>
            ))}
          </div>
          <button type="button" onClick={addRow} className="btn-secondary !px-3 !py-1.5 text-xs mt-3">
            + Add row
          </button>
        </div>

        <button type="submit" disabled={submitting} className="btn-primary w-full">
          {submitting ? 'Generating…' : 'Generate Seat Layout'}
        </button>
      </form>
    </div>
  )
}

/* ---------- Step 2: create an event, then put its seats on sale with tier pricing ---------- */

function EventSetup() {
  const [venues, setVenues] = useState([])
  const [event, setEvent] = useState(null) // set once created, unlocks the pricing form
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('')
  const [venueId, setVenueId] = useState('')
  const [eventDate, setEventDate] = useState('')
  const [posterFile, setPosterFile] = useState(null) // optional - null is fine, no poster required
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    api.get('/venues').then(({ data }) => setVenues(data))
  }, [])

  async function handleCreateEvent(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const { data } = await api.post('/events', {
        title,
        description,
        category,
        venueId: Number(venueId),
        eventDate: new Date(eventDate).toISOString(),
      })

      // Poster is optional - the event is already created either way, this
      // is just a second, best-effort step. If it fails, the event still
      // exists and a poster can be added later (re-run this upload against
      // the same event id).
      if (posterFile) {
        try {
          const formData = new FormData()
          formData.append('file', posterFile)
          const { data: withPoster } = await api.post(`/events/${data.id}/poster`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
          })
          setEvent(withPoster)
        } catch {
          setEvent(data)
          setError('Event created, but the poster upload failed. You can skip it and add one later.')
        }
      } else {
        setEvent(data)
      }
    } catch (err) {
//       setError(err.response?.data?.message || 'Failed to create event.')
        setError(extractErrorMessage(err, 'Failed to create event.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (event) {
    return <EventPricingForm event={event} onStartOver={() => setEvent(null)} />
  }

  return (
    <form onSubmit={handleCreateEvent} className="card p-6 space-y-4">
      {error && <p className="text-danger text-sm">{error}</p>}

      <Field label="Title">
        <input className="input-field" required value={title} onChange={(e) => setTitle(e.target.value)} />
      </Field>
      <Field label="Description">
        <textarea
          className="input-field"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Category">
          <input
            className="input-field"
            required
            placeholder="Concert, Movie, Sports…"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
        </Field>
        <Field label="Venue">
          <select className="input-field" required value={venueId} onChange={(e) => setVenueId(e.target.value)}>
            <option value="">Select venue</option>
            {venues.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name} — {v.city}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field label="Event date & time">
        <input
          type="datetime-local"
          className="input-field"
          required
          value={eventDate}
          onChange={(e) => setEventDate(e.target.value)}
        />
      </Field>
      <Field label="Poster image (optional)">
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="input-field"
          onChange={(e) => setPosterFile(e.target.files?.[0] || null)}
        />
      </Field>

      <button type="submit" disabled={submitting} className="btn-primary w-full">
        {submitting ? 'Creating…' : 'Create Event'}
      </button>

      <p className="text-[11px] text-paper-muted text-center">
        Make sure the selected venue already has its seat layout generated (see tab 1),
        or seat pricing in the next step will have nothing to price.
      </p>
    </form>
  )
}

function EventPricingForm({ event, onStartOver }) {
  const [goldPrice, setGoldPrice] = useState('1500')
  const [silverPrice, setSilverPrice] = useState('800')
  const [platinumPrice, setPlatinumPrice] = useState('3000')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      const { data } = await api.post(`/events/${event.id}/seats/generate`, {
        tierPrices: {
          GOLD: Number(goldPrice),
          SILVER: Number(silverPrice),
          PLATINUM: Number(platinumPrice),
        },
      })
      setMessage(`${data.length} seats are now on sale for "${event.title}".`)
    } catch (err) {
//       setError(err.response?.data?.message || 'Failed to generate event seats.')
         setError(extractErrorMessage(err, 'Failed to generate event seats.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="card p-4 flex justify-between items-center">
        <p className="text-sm text-paper-muted">
          Event created: <span className="text-paper font-medium">{event.title}</span>
        </p>
        <button onClick={onStartOver} className="text-xs text-gold hover:underline">
          + New event instead
        </button>
      </div>

      <form onSubmit={handleSubmit} className="card p-6 space-y-4">
        {message && <p className="text-stub-available text-sm">{message}</p>}
        {error && <p className="text-danger text-sm">{error}</p>}

        <p className="text-xs text-paper-muted">
          Set a price per seat tier. One EventSeat gets created for every physical seat
          in the venue, priced by its tier.
        </p>

        <div className="grid grid-cols-3 gap-4">
          <Field label="Gold price (₹)">
            <input type="number" className="input-field" required value={goldPrice} onChange={(e) => setGoldPrice(e.target.value)} />
          </Field>
          <Field label="Silver price (₹)">
            <input type="number" className="input-field" required value={silverPrice} onChange={(e) => setSilverPrice(e.target.value)} />
          </Field>
          <Field label="Platinum price (₹)">
            <input type="number" className="input-field" required value={platinumPrice} onChange={(e) => setPlatinumPrice(e.target.value)} />
          </Field>
        </div>

        <button type="submit" disabled={submitting} className="btn-primary w-full">
          {submitting ? 'Putting on sale…' : 'Generate Event Seats & Go Live'}
        </button>
      </form>
    </div>
  )
}

function Field({ label, children }) {
  return (
    <div>
      <label className="block text-xs text-paper-muted mb-1.5">{label}</label>
      {children}
    </div>
  )
}
