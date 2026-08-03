import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import logo from '../assets/stubline-logo-full.png'

export default function Navbar() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <header className="border-b border-ink-line bg-ink sticky top-0 z-10">
      <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
        <Link to="/events" className="flex items-center">
          <img src={logo} alt="StubLine" className="h-11 w-auto" />
        </Link>

        <nav className="flex items-center gap-6 text-sm font-medium">
          <Link to="/events" className="text-paper-muted hover:text-paper transition-colors">
            Events
          </Link>
          {user && (
            <Link to="/my-bookings" className="text-paper-muted hover:text-paper transition-colors">
              My Bookings
            </Link>
          )}
          {isAdmin && (
            <Link to="/admin" className="text-paper-muted hover:text-paper transition-colors">
              Admin
            </Link>
          )}

          {user ? (
            <div className="flex items-center gap-4 pl-4 border-l border-ink-line">
              <Link to="/profile" className="text-paper-muted hover:text-paper transition-colors font-mono text-xs">
                {user.name}
              </Link>
              <button onClick={handleLogout} className="btn-secondary !px-3 !py-1.5 text-xs">
                Log out
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-3 pl-4 border-l border-ink-line">
              <Link to="/login" className="text-paper-muted hover:text-paper transition-colors">
                Log in
              </Link>
              <Link to="/register" className="btn-primary !px-4 !py-1.5 text-xs">
                Sign up
              </Link>
            </div>
          )}
        </nav>
      </div>
    </header>
  )
}