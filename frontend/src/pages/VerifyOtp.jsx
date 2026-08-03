import { useState } from 'react'
import { useLocation, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Shared between two flows, distinguished by `purpose` passed via router
 * state: 'signup' (after Register.jsx) and 'login' (after requesting an
 * OTP login code) both land here, since the UI (enter a 6-digit code) is
 * identical - only which backend endpoint gets called differs.
 */
export default function VerifyOtp() {
  const location = useLocation()
  const navigate = useNavigate()
  const { verifySignupOtp, loginWithOtp, requestLoginOtp } = useAuth()

  const email = location.state?.email
  const purpose = location.state?.purpose || 'signup'

  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [resendMessage, setResendMessage] = useState('')

  if (!email) {
    // Landed here directly without going through register/OTP-request first
    return (
      <div className="max-w-md mx-auto mt-16 px-6 text-center">
        <p className="text-paper-muted">
          Nothing to verify yet.{' '}
          <Link to="/register" className="text-gold hover:underline">
            Create an account
          </Link>{' '}
          first.
        </p>
      </div>
    )
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      if (purpose === 'signup') {
        await verifySignupOtp(email, code)
      } else {
        await loginWithOtp(email, code)
      }
      navigate('/events')
    } catch (err) {
      setError(err.response?.data?.message || 'Verification failed.')
    } finally {
      setLoading(false)
    }
  }

  async function handleResend() {
    setError('')
    setResendMessage('')
    try {
      if (purpose === 'login') {
        await requestLoginOtp(email)
      }
      // Signup-OTP resend reuses the same request-a-fresh-code path as login
      // OTP does server-side (see AuthService) - for signup specifically the
      // original register() call already sent one, so a dedicated "resend"
      // for that path isn't wired up separately here; asking the person to
      // re-check their inbox/spam is the practical fallback in that case.
      setResendMessage(purpose === 'login' ? 'A new code has been sent.' : 'Check your inbox (and spam folder) for the original code.')
    } catch (err) {
      setError(err.response?.data?.message || 'Could not resend code.')
    }
  }

  return (
    <div className="max-w-md mx-auto mt-16 px-6">
      <div className="card p-8">
        <h1 className="font-display text-4xl text-gold mb-1">Enter your code</h1>
        <p className="text-paper-muted text-sm mb-8">
          We sent a 6-digit code to <span className="text-paper">{email}</span>.
        </p>

        {error && (
          <div className="bg-danger/10 border border-danger text-danger text-sm rounded-md px-4 py-2.5 mb-5">
            {error}
          </div>
        )}
        {resendMessage && (
          <div className="bg-stub-available/10 border border-stub-available text-stub-available text-sm rounded-md px-4 py-2.5 mb-5">
            {resendMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs text-paper-muted mb-1.5">Verification code</label>
            <input
              type="text"
              inputMode="numeric"
              pattern="\d{6}"
              maxLength={6}
              required
              autoFocus
              className="input-field text-center text-2xl tracking-[0.5em] font-mono"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="000000"
            />
          </div>
          <button type="submit" disabled={loading || code.length !== 6} className="btn-primary w-full mt-2">
            {loading ? 'Verifying…' : 'Verify'}
          </button>
        </form>

        <button onClick={handleResend} className="text-center text-sm text-gold hover:underline w-full mt-6">
          Didn't get a code? Resend
        </button>
      </div>
    </div>
  )
}
