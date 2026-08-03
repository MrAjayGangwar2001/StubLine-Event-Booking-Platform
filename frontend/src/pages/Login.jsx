import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import GoogleSignInButton from '../components/GoogleSignInButton'

export default function Login() {
  const { login, requestLoginOtp } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState('password') // 'password' | 'otp'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handlePasswordSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email, password)
      navigate('/events')
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed. Check your credentials.')
    } finally {
      setLoading(false)
    }
  }

  async function handleOtpRequest(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await requestLoginOtp(email)
      navigate('/verify-otp', { state: { email, purpose: 'login' } })
    } catch (err) {
      setError(err.response?.data?.message || 'Could not send login code.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-md mx-auto mt-16 px-6">
      <div className="card p-8">
        <h1 className="font-display text-4xl text-gold mb-1">Welcome back</h1>
        <p className="text-paper-muted text-sm mb-8">Log in to book your next event.</p>

        {error && (
          <div className="bg-danger/10 border border-danger text-danger text-sm rounded-md px-4 py-2.5 mb-5">
            {error}
          </div>
        )}

        <GoogleSignInButton />

        <div className="flex items-center gap-3 my-5">
          <div className="h-px flex-1 bg-ink-line" />
          <span className="text-xs text-paper-muted">or</span>
          <div className="h-px flex-1 bg-ink-line" />
        </div>

        <div className="flex gap-2 mb-5">
          <ModeButton active={mode === 'password'} onClick={() => setMode('password')}>
            Password
          </ModeButton>
          <ModeButton active={mode === 'otp'} onClick={() => setMode('otp')}>
            Email code
          </ModeButton>
        </div>

        {mode === 'password' ? (
          <form onSubmit={handlePasswordSubmit} className="space-y-4">
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
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs text-paper-muted">Password</label>
                <Link to="/forgot-password" className="text-xs text-gold hover:underline">
                  Forgot password?
                </Link>
              </div>
              <input
                type="password"
                required
                className="input-field"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </div>
            <button type="submit" disabled={loading} className="btn-primary w-full mt-2">
              {loading ? 'Logging in…' : 'Log in'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleOtpRequest} className="space-y-4">
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
            <button type="submit" disabled={loading} className="btn-primary w-full mt-2">
              {loading ? 'Sending code…' : 'Send login code'}
            </button>
            <p className="text-[11px] text-paper-muted text-center">
              We'll email you a 6-digit code - no password needed.
            </p>
          </form>
        )}

        <p className="text-center text-sm text-paper-muted mt-6">
          New here?{' '}
          <Link to="/register" className="text-gold hover:underline">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  )
}

function ModeButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 py-2 rounded-md text-sm font-medium transition-colors ${
        active ? 'bg-gold text-ink' : 'bg-ink-soft text-paper-muted hover:text-paper'
      }`}
    >
      {children}
    </button>
  )
}
