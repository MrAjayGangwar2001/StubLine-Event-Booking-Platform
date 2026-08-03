import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import GoogleSignInButton from '../components/GoogleSignInButton'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await register(name, email, password)
      // Account isn't usable yet - it needs the OTP emailed to them verified first.
      navigate('/verify-otp', { state: { email, purpose: 'signup' } })
    } catch (err) {
      const data = err.response?.data
      const message = data?.message || Object.values(data || {})[0] || 'Registration failed.'
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-md mx-auto mt-16 px-6">
      <div className="card p-8">
        <h1 className="font-display text-4xl text-gold mb-1">Get your seat</h1>
        <p className="text-paper-muted text-sm mb-8">Create an account to start booking.</p>

        {error && (
          <div className="bg-danger/10 border border-danger text-danger text-sm rounded-md px-4 py-2.5 mb-5">
            {error}
          </div>
        )}

        <GoogleSignInButton />

        <div className="flex items-center gap-3 my-5">
          <div className="h-px flex-1 bg-ink-line" />
          <span className="text-xs text-paper-muted">or sign up with email</span>
          <div className="h-px flex-1 bg-ink-line" />
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs text-paper-muted mb-1.5">Full name</label>
            <input
              type="text"
              required
              className="input-field"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ajay Gangwar"
            />
          </div>
          <div>
            <label className="block text-xs text-paper-muted mb-1.5">Email</label>
            <input
              type="email"
              required
              className="input-field"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />
          </div>
          <div>
            <label className="block text-xs text-paper-muted mb-1.5">Password</label>
            <input
              type="password"
              required
              minLength={6}
              className="input-field"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 6 characters"
            />
          </div>
          <button type="submit" disabled={loading} className="btn-primary w-full mt-2">
            {loading ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="text-center text-sm text-paper-muted mt-6">
          Already have an account?{' '}
          <Link to="/login" className="text-gold hover:underline">
            Log in
          </Link>
        </p>
      </div>
    </div>
  )
}
