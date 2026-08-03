import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { ASSET_BASE_URL } from '../api/axios'
import AnnouncementBanner from '../components/AnnouncementBanner'

export default function Events() {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get('/events')
      .then(({ data }) => setEvents(data))
      .catch(() => setError('Could not load events. Is the backend running?'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <>
      <AnnouncementBanner />
      <div className="max-w-6xl mx-auto px-6 py-12">
        <div className="mb-10">
        <p className="text-gold text-xs tracking-[0.2em] uppercase mb-2">Now booking</p>
        <h1 className="font-display text-5xl">Upcoming Events</h1>
      </div>

      {loading && <p className="text-paper-muted">Loading events…</p>}
      {error && <p className="text-danger">{error}</p>}
      {!loading && !error && events.length === 0 && (
        <div className="card p-10 text-center">
          <p className="text-paper-muted">No events yet. Check back soon, or ask an admin to add one.</p>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {events.map((event) => (
          <Link
            key={event.id}
            to={`/events/${event.id}`}
            className="card overflow-hidden hover:border-gold transition-colors group"
          >
            {event.posterImageUrl && (
              <img
                src={`${ASSET_BASE_URL}${event.posterImageUrl}`}
                alt={`${event.title} poster`}
                className="w-full h-40 object-cover"
              />
            )}
            <div className="p-6">
              <p className="text-xs text-gold uppercase tracking-wide mb-2">{event.category}</p>
              <h2 className="font-display text-2xl mb-2 group-hover:text-gold transition-colors">
                {event.title}
              </h2>
              <p className="text-sm text-paper-muted mb-4 line-clamp-2">{event.description}</p>
              <div className="text-xs font-mono text-paper-muted space-y-1 pt-4 border-t border-ink-line">
                <p>{new Date(event.eventDate).toLocaleString()}</p>
                <p>{event.venue.name} · {event.venue.city}</p>
              </div>
            </div>
          </Link>
        ))}
      </div>
      </div>
    </>
  )
}
