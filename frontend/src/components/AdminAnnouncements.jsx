import { useEffect, useState } from 'react'
import api from '../api/axios'

const SEVERITY_STYLES = {
  INFO: 'bg-gold/20 text-gold border-gold',
  WARNING: 'bg-stub-locked/20 text-stub-locked border-stub-locked',
  URGENT: 'bg-danger/20 text-danger border-danger',
}

export default function AdminAnnouncements() {
  const [announcements, setAnnouncements] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [severity, setSeverity] = useState('INFO')
  const [submitting, setSubmitting] = useState(false)
  const [actioningId, setActioningId] = useState(null)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get('/announcements/admin/all')
      setAnnouncements(data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load announcements.')
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate(e) {
    e.preventDefault()
    if (!message.trim()) return
    setSubmitting(true)
    setError('')
    try {
      await api.post('/announcements', { message: message.trim(), severity })
      setMessage('')
      setSeverity('INFO')
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create announcement.')
    } finally {
      setSubmitting(false)
    }
  }

  async function toggleActive(a) {
    setActioningId(a.id)
    try {
      await api.post(`/announcements/${a.id}/${a.active ? 'deactivate' : 'activate'}`)
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'Action failed.')
    } finally {
      setActioningId(null)
    }
  }

  async function handleDelete(a) {
    if (!window.confirm('Delete this announcement permanently?')) return
    setActioningId(a.id)
    try {
      await api.delete(`/announcements/${a.id}`)
      await load()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete.')
    } finally {
      setActioningId(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="card p-6">
        <h2 className="font-display text-2xl text-gold mb-4">New Announcement</h2>
        <form onSubmit={handleCreate} className="space-y-4">
          <textarea
            className="input-field w-full"
            rows={3}
            placeholder="e.g. Site maintenance tonight 11 PM - 12 AM IST"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={500}
            required
          />
          <div className="flex items-center gap-3">
            <select className="input-field" value={severity} onChange={(e) => setSeverity(e.target.value)}>
              <option value="INFO">Info</option>
              <option value="WARNING">Warning</option>
              <option value="URGENT">Urgent</option>
            </select>
            <button type="submit" disabled={submitting} className="btn-primary !px-4 !py-2 text-sm">
              {submitting ? 'Publishing…' : 'Publish'}
            </button>
          </div>
        </form>
      </div>

      <div className="card p-6">
        <h2 className="font-display text-2xl text-gold mb-4">All Announcements</h2>
        {error && <p className="text-danger text-sm mb-4">{error}</p>}
        {loading && <p className="text-paper-muted text-sm">Loading…</p>}
        {!loading && announcements.length === 0 && (
          <p className="text-paper-muted text-sm">No announcements yet.</p>
        )}
        <div className="space-y-2">
          {announcements.map((a) => {
            const busy = actioningId === a.id
            return (
              <div key={a.id} className="flex items-center justify-between gap-4 border border-ink-line rounded-md p-4">
                <div>
                  <span className={`text-xs px-2 py-0.5 rounded-full border font-mono mr-2 ${SEVERITY_STYLES[a.severity]}`}>
                    {a.severity}
                  </span>
                  <span className={a.active ? '' : 'text-paper-muted line-through'}>{a.message}</span>
                </div>
                <div className="flex gap-3 shrink-0">
                  <button
                    onClick={() => toggleActive(a)}
                    disabled={busy}
                    className="text-xs text-gold hover:underline disabled:opacity-50"
                  >
                    {a.active ? 'Deactivate' : 'Activate'}
                  </button>
                  <button
                    onClick={() => handleDelete(a)}
                    disabled={busy}
                    className="text-xs text-danger hover:underline disabled:opacity-50"
                  >
                    Delete
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
