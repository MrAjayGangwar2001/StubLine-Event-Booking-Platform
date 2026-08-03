import { useEffect, useState } from 'react'
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend,
} from 'recharts'
import api from '../api/axios'

const GOLD = '#c9a24b'
const GOLD_MUTED = '#8a7239'

export default function AdminAnalytics() {
  const [summary, setSummary] = useState(null)
  const [events, setEvents] = useState([])
  const [timeline, setTimeline] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    loadAll()
  }, [])

  async function loadAll() {
    setLoading(true)
    setError('')
    try {
      const [summaryRes, eventsRes, timelineRes] = await Promise.all([
        api.get('/admin/analytics/summary'),
        api.get('/admin/analytics/events'),
        api.get('/admin/analytics/bookings-timeline?daysBack=30'),
      ])
      setSummary(summaryRes.data)
      setEvents(eventsRes.data)
      setTimeline(timelineRes.data)
    } catch (err) {
      setError(err.response?.data?.message || 'Could not load analytics.')
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <p className="text-paper-muted text-sm">Loading analytics…</p>
  if (error) return <p className="text-danger text-sm">{error}</p>
  if (!summary) return null

  return (
    <div className="space-y-8">
      {/* Summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <SummaryCard label="Total Revenue" value={`₹${Number(summary.totalRevenue).toLocaleString('en-IN')}`} />
        <SummaryCard label="Confirmed Bookings" value={summary.totalConfirmedBookings} />
        <SummaryCard label="Pending Checkouts" value={summary.totalPendingBookings} />
        <SummaryCard label="Upcoming Events" value={`${summary.upcomingEvents} / ${summary.totalEvents}`} />
        <SummaryCard label="Overall Occupancy" value={`${summary.overallOccupancyPercent}%`} />
      </div>

      {/* Revenue by event */}
      <div className="card p-6">
        <h3 className="font-display text-xl text-gold mb-4">Revenue by Event</h3>
        {events.length === 0 ? (
          <p className="text-sm text-paper-muted">No events yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={events} margin={{ top: 10, right: 10, left: 0, bottom: 40 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2a2a2a" />
              <XAxis
                dataKey="title"
                tick={{ fill: '#a89f8f', fontSize: 11 }}
                angle={-30}
                textAnchor="end"
                interval={0}
              />
              <YAxis tick={{ fill: '#a89f8f', fontSize: 11 }} />
              <Tooltip
                contentStyle={{ background: '#1a1a1a', border: '1px solid #333', borderRadius: 8 }}
                labelStyle={{ color: '#e8e0d0' }}
                formatter={(value) => [`₹${Number(value).toLocaleString('en-IN')}`, 'Revenue']}
              />
              <Bar dataKey="revenue" fill={GOLD} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Occupancy by event */}
      <div className="card p-6">
        <h3 className="font-display text-xl text-gold mb-4">Occupancy by Event</h3>
        {events.length === 0 ? (
          <p className="text-sm text-paper-muted">No events yet.</p>
        ) : (
          <div className="space-y-3">
            {events.map((e) => (
              <div key={e.eventId}>
                <div className="flex justify-between text-xs text-paper-muted mb-1">
                  <span>{e.title}</span>
                  <span>{e.bookedSeats}/{e.totalSeats} seats · {e.occupancyPercent}%</span>
                </div>
                <div className="h-2 bg-ink-soft rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gold rounded-full transition-all"
                    style={{ width: `${e.occupancyPercent}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Bookings timeline */}
      <div className="card p-6">
        <h3 className="font-display text-xl text-gold mb-4">Confirmed Bookings — Last 30 Days</h3>
        {timeline.length === 0 ? (
          <p className="text-sm text-paper-muted">No confirmed bookings in this window yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={timeline} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2a2a2a" />
              <XAxis dataKey="date" tick={{ fill: '#a89f8f', fontSize: 11 }} />
              <YAxis yAxisId="left" tick={{ fill: '#a89f8f', fontSize: 11 }} />
              <YAxis yAxisId="right" orientation="right" tick={{ fill: '#a89f8f', fontSize: 11 }} />
              <Tooltip
                contentStyle={{ background: '#1a1a1a', border: '1px solid #333', borderRadius: 8 }}
                labelStyle={{ color: '#e8e0d0' }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Line yAxisId="left" type="monotone" dataKey="confirmedBookings" name="Bookings" stroke={GOLD} strokeWidth={2} dot={false} />
              <Line yAxisId="right" type="monotone" dataKey="revenue" name="Revenue (₹)" stroke={GOLD_MUTED} strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}

function SummaryCard({ label, value }) {
  return (
    <div className="card p-4">
      <p className="text-[11px] uppercase tracking-wider text-paper-muted mb-1">{label}</p>
      <p className="font-display text-2xl text-gold">{value}</p>
    </div>
  )
}
