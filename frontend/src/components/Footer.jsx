import { Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { SUPPORT_EMAIL } from '../constants/contact'
import logo from '../assets/stubline-logo-full.svg'
import FeedbackDrawer from './FeedbackDrawer'
import api from '../api/axios'

export default function Footer() {
  const { user, isAdmin, isSuperAdmin } = useAuth()
  const year = new Date().getFullYear()
  const [feedbackOpen, setFeedbackOpen] = useState(false)
  const [visitorCount, setVisitorCount] = useState(null)

  useEffect(() => {
    if (!isSuperAdmin) return
    api
      .get('/admin/analytics/visitor-count')
      .then(({ data }) => setVisitorCount(data))
      .catch(() => {})
  }, [isSuperAdmin])

  return (
    <>
    <footer className="border-t border-ink-line bg-ink mt-16">
      <div className="max-w-6xl mx-auto px-6 py-12 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-10">
        <div>
          <img src={logo} alt="StubLine" className="h-7 w-auto mb-3" />
          <p className="text-paper-muted text-sm leading-relaxed">
            Real-time event booking, seat selection, and secure payments — all in one place.
          </p>
        </div>

        <div>
          <h3 className="font-display text-lg text-gold mb-3 tracking-wide">Quick Links</h3>
          <ul className="space-y-2 text-sm">
            <li>
              <Link to="/events" className="text-paper-muted hover:text-paper transition-colors">
                Browse Events
              </Link>
            </li>
            {user && (
              <li>
                <Link to="/my-bookings" className="text-paper-muted hover:text-paper transition-colors">
                  My Bookings
                </Link>
              </li>
            )}
            {user && (
              <li>
                <Link to="/profile" className="text-paper-muted hover:text-paper transition-colors">
                  Profile
                </Link>
              </li>
            )}
            {isAdmin && (
              <li>
                <Link to="/admin" className="text-paper-muted hover:text-paper transition-colors">
                  Admin Dashboard
                </Link>
              </li>
            )}
            {!user && (
              <li>
                <Link to="/register" className="text-paper-muted hover:text-paper transition-colors">
                  Sign Up
                </Link>
              </li>
            )}
          </ul>
        </div>

        <div>
          <h3 className="font-display text-lg text-gold mb-3 tracking-wide">Support</h3>
          <ul className="space-y-2 text-sm">
            <li>
              <a href={`mailto:${SUPPORT_EMAIL}`} className="text-paper-muted hover:text-paper transition-colors">
                Contact Us
              </a>
            </li>
            <li>
              <button
                onClick={() => setFeedbackOpen(true)}
                className="text-paper-muted hover:text-paper transition-colors text-left"
              >
                Send Feedback
              </button>
            </li>
            <li>
              <a href={`mailto:${SUPPORT_EMAIL}?subject=Issue Report`} className="text-paper-muted hover:text-paper transition-colors">
                Report an Issue
              </a>
            </li>
          </ul>
        </div>

        <div>
          <h3 className="font-display text-lg text-gold mb-3 tracking-wide">Legal</h3>
          <ul className="space-y-2 text-sm">
            <li>
              <Link to="/terms" className="text-paper-muted hover:text-paper transition-colors">
                Terms of Service
              </Link>
            </li>
            <li>
              <Link to="/privacy" className="text-paper-muted hover:text-paper transition-colors">
                Privacy Policy
              </Link>
            </li>
            <li>
              <Link to="/refund-policy" className="text-paper-muted hover:text-paper transition-colors">
                Refund Policy
              </Link>
            </li>
          </ul>
        </div>
      </div>

      <div className="border-t border-ink-line">
        <div className="max-w-6xl mx-auto px-6 py-5 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-paper-muted">
          <p>&copy; {year} StubLine. All rights reserved.</p>
          {isSuperAdmin && visitorCount && (
            <p className="font-mono">
              👁 {visitorCount.totalVisits} total visits · {visitorCount.visitsToday} today
            </p>
          )}
          <p>
            Made with <span className="text-gold">&hearts;</span> for live events.
          </p>
        </div>
      </div>
    </footer>

    <FeedbackDrawer open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
    </>
  )
}
