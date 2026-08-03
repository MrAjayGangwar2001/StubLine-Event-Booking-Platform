import { useEffect, useState } from 'react'
import api from '../api/axios'
import EventActionModal from './EventActionModal'

const STATUS_STYLES = {
  UPCOMING: 'bg-stub-available/20 text-stub-available border-stub-available',
  COMPLETED: 'bg-paper-muted/20 text-paper-muted border-paper-muted',
  CANCELLED: 'bg-danger/20 text-danger border-danger',
}

export default function AdminEventsList() {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actioningId, setActioningId] = useState(null)
  const [modal, setModal] = useState(null) // { mode: 'cancel' | 'postpone', event } | null

  useEffect(() => {
    loadEvents()
  }, [])

  async function loadEvents() {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get('/events/admin/all')
      setEvents(data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load events.')
    } finally {
      setLoading(false)
    }
  }

  async function runAction(eventId, action) {
    setActioningId(eventId)
    setError('')
    try {
      await action()
      await loadEvents()
    } catch (err) {
      // Bug fix: this used to leave `events` untouched but the render
      // below returned early on `error` alone, wiping the whole list from
      // view the moment any action failed - the list is still in state
      // the whole time now, `error` is shown as a banner above it instead.
      setError(err.response?.data?.message || 'Action failed.')
    } finally {
      setActioningId(null)
    }
  }

  function handlePauseToggle(event) {
    const path = event.bookingEnabled === false ? 'resume-booking' : 'pause-booking'
    runAction(event.id, () => api.post(`/events/${event.id}/${path}`))
  }

  function handleModalConfirm(payload) {
    const { mode, event } = modal
    setModal(null)
    if (mode === 'cancel') {
      runAction(event.id, () => api.post(`/events/${event.id}/cancel`, payload))
    } else {
      runAction(event.id, () => api.post(`/events/${event.id}/postpone`, payload))
    }
  }

  if (loading) return <p className="text-paper-muted text-sm">Loading…</p>

  return (
    <div className="card p-6">
      <div className="flex justify-between items-center mb-6">
        <h2 className="font-display text-2xl text-gold">All Events</h2>
        <button onClick={loadEvents} className="text-xs text-gold hover:underline">
          Refresh
        </button>
      </div>

      {error && (
        <div className="flex items-center justify-between gap-4 border border-danger text-danger text-sm rounded-md px-4 py-2 mb-4">
          <span>{error}</span>
          <button onClick={() => setError('')} className="shrink-0 opacity-70 hover:opacity-100" aria-label="Dismiss">
            ✕
          </button>
        </div>
      )}

      {events.length === 0 ? (
        <p className="text-paper-muted text-sm">No events created yet.</p>
      ) : (
        <div className="space-y-2">
          {events.map((event) => {
            const busy = actioningId === event.id
            const isCancelled = event.status === 'CANCELLED'
            const isCompleted = event.status === 'COMPLETED'
            return (
              <div key={event.id} className="border border-ink-line rounded-md p-4">
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <p className="font-medium">{event.title}</p>
                    <p className="text-xs text-paper-muted font-mono mt-1">
                      {event.venue.name}, {event.venue.city} ·{' '}
                      {new Date(event.eventDate).toLocaleString()}
                    </p>
                    {isCancelled && event.cancellationReason && (
                      <p className="text-xs text-danger mt-1">Reason: {event.cancellationReason}</p>
                    )}
                    {!isCancelled && event.bookingEnabled === false && (
                      <p className="text-xs text-stub-locked mt-1">Bookings paused</p>
                    )}
                  </div>
                  <span
                    className={`text-xs px-2 py-1 rounded-full border font-mono shrink-0 ${STATUS_STYLES[event.status] || ''}`}
                  >
                    {event.status}
                  </span>
                </div>

                {!isCancelled && !isCompleted && (
                  <div className="flex flex-wrap gap-3 mt-3 pt-3 border-t border-ink-line">
                    <button
                      onClick={() => handlePauseToggle(event)}
                      disabled={busy}
                      className="text-xs text-gold hover:underline disabled:opacity-50"
                    >
                      {event.bookingEnabled === false ? 'Resume booking' : 'Pause booking'}
                    </button>
                    <button
                      onClick={() => setModal({ mode: 'postpone', event })}
                      disabled={busy}
                      className="text-xs text-gold hover:underline disabled:opacity-50"
                    >
                      Postpone
                    </button>
                    <button
                      onClick={() => setModal({ mode: 'cancel', event })}
                      disabled={busy}
                      className="text-xs text-danger hover:underline disabled:opacity-50"
                    >
                      Cancel event
                    </button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {modal && (
        <EventActionModal
          mode={modal.mode}
          eventTitle={modal.event.title}
          onCancel={() => setModal(null)}
          onConfirm={handleModalConfirm}
        />
      )}
    </div>
  )
}
