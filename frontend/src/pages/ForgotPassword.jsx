import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../api/axios'

export default function ForgotPassword() {
  const navigate = useNavigate()
  const [step, setStep] = useState('email') // 'email' | 'reset'
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleRequestCode(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      // Backend explicitly confirms/denies whether this email is registered
      // (see AuthService.forgotPasswordRequest's comment on that trade-off) -
      // so a clear "not registered" message surfaces right here rather than
      // silently pretending a code was sent either way.
      await api.post('/auth/forgot-password/request', { email })
      setStep('reset')
    } catch (err) {
      setError(err.response?.data?.message || 'Could not send reset code.')
    } finally {
      setLoading(false)
    }
  }

  async function handleResend() {
    setError('')
    setInfo('')
    try {
      await api.post('/auth/forgot-password/request', { email })
      setInfo('A new code has been sent.')
    } catch (err) {
      setError(err.response?.data?.message || 'Could not resend code.')
    }
  }

  async function handleResetPassword(e) {
    e.preventDefault()
    setError('')

    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match.')
      return
    }

    setLoading(true)
    try {
      await api.post('/auth/forgot-password/reset', { email, code, newPassword })
      navigate('/login', { state: { justResetPassword: true } })
    } catch (err) {
      setError(err.response?.data?.message || 'Could not reset password.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-md mx-auto mt-16 px-6">
      <div className="card p-8">
        <h1 className="font-display text-4xl text-gold mb-1">Reset password</h1>
        <p className="text-paper-muted text-sm mb-8">
          {step === 'email'
            ? "Enter the email on your account and we'll send you a code."
            : <>Enter the code sent to <span className="text-paper">{email}</span> and choose a new password.</>}
        </p>

        {error && (
          <div className="bg-danger/10 border border-danger text-danger text-sm rounded-md px-4 py-2.5 mb-5">
            {error}
          </div>
        )}
        {info && (
          <div className="bg-stub-available/10 border border-stub-available text-stub-available text-sm rounded-md px-4 py-2.5 mb-5">
            {info}
          </div>
        )}

        {step === 'email' ? (
          <form onSubmit={handleRequestCode} className="space-y-4">
            <div>
              <label className="block text-xs text-paper-muted mb-1.5">Email</label>
              <input
                type="email"
                required
                autoFocus
                className="input-field"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />
            </div>
            <button type="submit" disabled={loading} className="btn-primary w-full mt-2">
              {loading ? 'Sending…' : 'Send reset code'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleResetPassword} className="space-y-4">
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
            <button type="submit" disabled={loading || code.length !== 6} className="btn-primary w-full mt-2">
              {loading ? 'Updating…' : 'Reset password'}
            </button>
            <button type="button" onClick={handleResend} className="text-center text-sm text-gold hover:underline w-full">
              Didn't get a code? Resend
            </button>
          </form>
        )}

        <p className="text-center text-sm text-paper-muted mt-6">
          <Link to="/login" className="text-gold hover:underline">
            Back to login
          </Link>
        </p>
      </div>
    </div>
  )
}
