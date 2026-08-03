import { useEffect, useState } from 'react'
import api from '../api/axios'

const ROLE_STYLES = {
  USER: 'bg-paper-muted/20 text-paper-muted border-paper-muted',
  ADMIN: 'bg-gold/20 text-gold border-gold',
  SUPER_ADMIN: 'bg-danger/20 text-danger border-danger',
}

export default function AdminUserManagement() {
  const [search, setSearch] = useState('')
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actioningId, setActioningId] = useState(null)

  useEffect(() => {
    load()
  }, [])

  async function load(query) {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get('/admin/users', { params: query ? { search: query } : {} })
      setUsers(data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load users.')
    } finally {
      setLoading(false)
    }
  }

  function handleSearch(e) {
    e.preventDefault()
    load(search)
  }

  async function handlePromote(user) {
    if (!window.confirm(`Make ${user.name} (${user.email}) an admin?`)) return
    setActioningId(user.id)
    setError('')
    try {
      await api.post(`/admin/users/${user.id}/promote`)
      await load(search)
    } catch (err) {
      setError(err.response?.data?.message || 'Action failed.')
    } finally {
      setActioningId(null)
    }
  }

  async function handleDemote(user) {
    if (!window.confirm(`Remove admin access from ${user.name} (${user.email})?`)) return
    setActioningId(user.id)
    setError('')
    try {
      await api.post(`/admin/users/${user.id}/demote`)
      await load(search)
    } catch (err) {
      setError(err.response?.data?.message || 'Action failed.')
    } finally {
      setActioningId(null)
    }
  }

  return (
    <div className="card p-6">
      <h2 className="font-display text-2xl text-gold mb-2">Manage Admins</h2>
      <p className="text-xs text-paper-muted mb-4">
        Only a super admin can promote or demote other users - regular admins don't have access to this screen.
      </p>

      <form onSubmit={handleSearch} className="flex gap-3 mb-6">
        <input
          type="text"
          className="input-field flex-1"
          placeholder="Search by name or email…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button type="submit" className="btn-secondary !px-4 !py-2 text-sm">
          Search
        </button>
      </form>

      {error && <p className="text-danger text-sm mb-4">{error}</p>}
      {loading && <p className="text-paper-muted text-sm">Loading…</p>}
      {!loading && users.length === 0 && <p className="text-paper-muted text-sm">No users found.</p>}

      <div className="space-y-2">
        {users.map((u) => {
          const busy = actioningId === u.id
          return (
            <div key={u.id} className="flex items-center justify-between gap-4 border border-ink-line rounded-md p-4">
              <div>
                <p className="font-medium">{u.name}</p>
                <p className="text-xs text-paper-muted font-mono">{u.email}</p>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <span className={`text-xs px-2 py-1 rounded-full border font-mono ${ROLE_STYLES[u.role]}`}>
                  {u.role}
                </span>
                {u.role === 'USER' && (
                  <button
                    onClick={() => handlePromote(u)}
                    disabled={busy}
                    className="text-xs text-gold hover:underline disabled:opacity-50"
                  >
                    Make admin
                  </button>
                )}
                {u.role === 'ADMIN' && (
                  <button
                    onClick={() => handleDemote(u)}
                    disabled={busy}
                    className="text-xs text-danger hover:underline disabled:opacity-50"
                  >
                    Remove admin
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
