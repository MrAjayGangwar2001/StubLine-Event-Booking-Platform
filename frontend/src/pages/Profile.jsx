import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'

export default function Profile() {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    loadProfile()
  }, [])

  async function loadProfile() {
    setLoading(true)
    try {
      const { data } = await api.get('/users/me')
      setProfile(data)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load profile.')
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <p className="text-paper-muted text-center py-20">Loading…</p>
  if (error) return <p className="text-danger text-center py-20">{error}</p>
  if (!profile) return null

  return (
    <div className="max-w-2xl mx-auto px-6 py-12 space-y-8">
      <div>
        <h1 className="font-display text-5xl mb-2">Your Profile</h1>
        <p className="text-paper-muted text-sm">Update your personal details.</p>
      </div>

      <ProfileForm profile={profile} onUpdated={setProfile} />

      {profile.authProvider === 'LOCAL' ? (
        <ChangePasswordForm />
      ) : (
        <div className="card p-6">
          <h2 className="font-display text-2xl text-gold mb-2">Password</h2>
          <p className="text-sm text-paper-muted mb-3">
            This account signed up with Google and has no password set yet. You can still log in
            from any device without your Google session - via a one-time email code, or by setting
            a password below.
          </p>
          <Link to="/forgot-password" className="text-sm text-gold hover:underline">
            Set a password →
          </Link>
        </div>
      )}
    </div>
  )
}

function ProfileForm({ profile, onUpdated }) {
  const [name, setName] = useState(profile.name || '')
  const [gender, setGender] = useState(profile.gender || '')
  const [phoneNumber, setPhoneNumber] = useState(profile.phoneNumber || '')
  const [address, setAddress] = useState(profile.address || '')
  const [bio, setBio] = useState(profile.bio || '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      const { data } = await api.put('/users/me', { name, gender, phoneNumber, address, bio })
      onUpdated(data)
      // Keep the navbar's cached name in sync, since it reads from localStorage.
      const stored = JSON.parse(localStorage.getItem('user') || '{}')
      localStorage.setItem('user', JSON.stringify({ ...stored, name: data.name }))
      setSuccess('Profile updated.')
    } catch (err) {
      const data = err.response?.data
      setError(data?.message || Object.values(data || {})[0] || 'Failed to update profile.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card p-6 space-y-4">
      <h2 className="font-display text-2xl text-gold mb-2">Personal Details</h2>

      {error && <p className="text-danger text-sm">{error}</p>}
      {success && <p className="text-stub-available text-sm">{success}</p>}

      <div>
        <label className="block text-xs text-paper-muted mb-1.5">Email</label>
        <input type="email" disabled value={profile.email} className="input-field opacity-60 cursor-not-allowed" />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs text-paper-muted mb-1.5">Full name</label>
          <input required className="input-field" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs text-paper-muted mb-1.5">Gender</label>
          <select className="input-field" value={gender} onChange={(e) => setGender(e.target.value)}>
            <option value="">Prefer not to say</option>
            <option value="Male">Male</option>
            <option value="Female">Female</option>
            <option value="Other">Other</option>
          </select>
        </div>
      </div>

      <div>
        <label className="block text-xs text-paper-muted mb-1.5">Phone number</label>
        <input className="input-field" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Optional" />
      </div>

      <div>
        <label className="block text-xs text-paper-muted mb-1.5">Address</label>
        <input className="input-field" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Optional" />
      </div>

      <div>
        <label className="block text-xs text-paper-muted mb-1.5">Bio</label>
        <textarea
          className="input-field"
          rows={3}
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          placeholder="Optional - a short line about yourself"
        />
      </div>

      <button type="submit" disabled={saving} className="btn-primary w-full">
        {saving ? 'Saving…' : 'Save changes'}
      </button>
    </form>
  )
}

function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')

    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match.')
      return
    }

    setSaving(true)
    try {
      await api.put('/users/me/password', { currentPassword, newPassword })
      setSuccess('Password updated.')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update password.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card p-6 space-y-4">
      <h2 className="font-display text-2xl text-gold mb-2">Change Password</h2>

      {error && <p className="text-danger text-sm">{error}</p>}
      {success && <p className="text-stub-available text-sm">{success}</p>}

      <div>
        <label className="block text-xs text-paper-muted mb-1.5">Current password</label>
        <input
          type="password"
          required
          className="input-field"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs text-paper-muted mb-1.5">New password</label>
          <input
            type="password"
            required
            minLength={6}
            className="input-field"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-xs text-paper-muted mb-1.5">Confirm new password</label>
          <input
            type="password"
            required
            minLength={6}
            className="input-field"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
        </div>
      </div>
      <button type="submit" disabled={saving} className="btn-primary w-full">
        {saving ? 'Updating…' : 'Update password'}
      </button>
    </form>
  )
}
