import { useEffect, useState } from 'react'
import api from '../api/axios'

const SEVERITY_CONFIG = {
  INFO: {
    icon: '🎟️',
    border: 'border-gold',
    accent: 'bg-gold',
    text: 'text-gold',
    bg: 'bg-gold/10',
    label: 'Announcement',
  },
  WARNING: {
    icon: '⚠️',
    border: 'border-stub-locked',
    accent: 'bg-stub-locked',
    text: 'text-stub-locked',
    bg: 'bg-stub-locked/10',
    label: 'Heads up',
  },
  URGENT: {
    icon: '🚨',
    border: 'border-danger',
    accent: 'bg-danger',
    text: 'text-danger',
    bg: 'bg-danger/10',
    label: 'Urgent',
  },
}

export default function AnnouncementBanner() {
  const [announcements, setAnnouncements] = useState([])
  const [dismissedIds, setDismissedIds] = useState([])

  useEffect(() => {
    api
      .get('/announcements/active')
      .then(({ data }) => setAnnouncements(data))
      .catch(() => {}) // a failed announcement fetch shouldn't block the page - just show nothing
  }, [])

  const visible = announcements.filter((a) => !dismissedIds.includes(a.id))
  if (visible.length === 0) return null

  return (
    <div className="max-w-6xl mx-auto px-6 pt-8 space-y-3">
      {visible.map((a) => {
        const cfg = SEVERITY_CONFIG[a.severity] || SEVERITY_CONFIG.INFO
        return (
          <div
            key={a.id}
            className={`relative overflow-hidden rounded-lg border ${cfg.border} ${cfg.bg} shadow-sm`}
          >
            <div className={`absolute left-0 top-0 h-full w-1.5 ${cfg.accent}`} />
            <div className="flex items-start gap-3 pl-5 pr-4 py-3.5">
              <span className="text-lg leading-none mt-0.5 shrink-0">{cfg.icon}</span>
              <div className="flex-1 min-w-0">
                <p className={`text-[11px] font-mono font-bold uppercase tracking-widest ${cfg.text} mb-0.5`}>
                  {cfg.label}
                </p>
                <p className="text-sm text-paper leading-relaxed">{a.message}</p>
              </div>
              <button
                onClick={() => setDismissedIds((ids) => [...ids, a.id])}
                className={`shrink-0 ${cfg.text} opacity-60 hover:opacity-100 transition-opacity text-sm mt-0.5`}
                aria-label="Dismiss"
              >
                ✕
              </button>
            </div>
          </div>
        )
      })}
    </div>
  )
}
