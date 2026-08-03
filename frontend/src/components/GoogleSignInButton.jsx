import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID

/**
 * Uses Google Identity Services (the modern "Sign in with Google" JS SDK,
 * loaded via the <script> tag in index.html) rather than Spring Security's
 * server-side OAuth2 login flow. That flow assumes session-based,
 * server-rendered redirects - awkward to wire up cleanly against a
 * stateless JWT REST API + separate React SPA. This way, Google does the
 * identity check entirely client-side and hands back a signed ID token;
 * the backend's only job is verifying that token's signature and claims
 * (see GoogleTokenVerifierService) before trusting anything in it.
 */
export default function GoogleSignInButton() {
  const { googleLogin } = useAuth()
  const navigate = useNavigate()
  const buttonRef = useRef(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID) return

    function renderButton() {
      if (!window.google?.accounts?.id || !buttonRef.current) return

      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: handleCredentialResponse,
      })

      window.google.accounts.id.renderButton(buttonRef.current, {
        theme: 'filled_black',
        size: 'large',
        width: 360,
        text: 'continue_with',
      })
    }

    // The GIS script (loaded in index.html) may not have finished loading
    // yet by the time this component mounts - poll briefly rather than
    // assuming it's already there.
    if (window.google?.accounts?.id) {
      renderButton()
    } else {
      const interval = setInterval(() => {
        if (window.google?.accounts?.id) {
          renderButton()
          clearInterval(interval)
        }
      }, 200)
      return () => clearInterval(interval)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleCredentialResponse(response) {
    setError('')
    try {
      await googleLogin(response.credential)
      navigate('/events')
    } catch (err) {
      setError(err.response?.data?.message || 'Google sign-in failed. Please try again.')
    }
  }

  if (!GOOGLE_CLIENT_ID) {
    // Fails quietly rather than showing a broken/non-functional button -
    // Google Sign-In is an enhancement, not a requirement, for using this app.
    return null
  }

  return (
    <div>
      <div ref={buttonRef} className="flex justify-center" />
      {error && <p className="text-danger text-xs mt-2 text-center">{error}</p>}
    </div>
  )
}
