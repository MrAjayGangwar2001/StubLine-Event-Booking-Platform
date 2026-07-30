import { createContext, useContext, useEffect, useState } from 'react'
import api from '../api/axios'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    return stored ? JSON.parse(stored) : null
  })

  function persistSession(data) {
    localStorage.setItem('token', data.token)
    const userInfo = { name: data.name, email: data.email, role: data.role }
    localStorage.setItem('user', JSON.stringify(userInfo))
    setUser(userInfo)
  }

  // The `user` object in localStorage is a snapshot from whenever this
  // browser last logged in - it does NOT update itself if an admin
  // promotes/demotes this user in the meantime. Re-checking against
  // GET /users/me on every app load means refreshing actually re-verifies
  // the role instead of just re-reading the same stale cache.
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) return

    api
      .get('/users/me')
      .then(({ data }) => {
        const userInfo = { name: data.name, email: data.email, role: data.role }
        localStorage.setItem('user', JSON.stringify(userInfo))
        setUser(userInfo)
      })
      .catch(() => {
        // A failed refresh here (expired/invalid token) is already handled
        // by the axios response interceptor (401 -> clears storage, redirects
        // to /login) - nothing extra needed here.
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function login(email, password) {
    const { data } = await api.post('/auth/login', { email, password })
    persistSession(data)
    return data
  }

  async function register(name, email, password) {
    const { data } = await api.post('/auth/register', { name, email, password })
    return data
  }

  async function verifySignupOtp(email, code) {
    const { data } = await api.post('/auth/verify-signup-otp', { email, code })
    persistSession(data)
    return data
  }

  async function requestLoginOtp(email) {
    const { data } = await api.post('/auth/login/otp/request', { email })
    return data
  }

  async function loginWithOtp(email, code) {
    const { data } = await api.post('/auth/login/otp/verify', { email, code })
    persistSession(data)
    return data
  }

  async function googleLogin(credential) {
    const { data } = await api.post('/auth/google', { credential })
    persistSession(data)
    return data
  }

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }

  const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN'
  const isSuperAdmin = user?.role === 'SUPER_ADMIN'

  return (
    <AuthContext.Provider
      value={{ user, isAdmin, isSuperAdmin, login, register, verifySignupOtp, requestLoginOtp, loginWithOtp, googleLogin, logout }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}