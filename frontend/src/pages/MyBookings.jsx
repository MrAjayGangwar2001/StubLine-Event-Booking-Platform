import { useEffect, useState } from 'react'
import api from '../api/axios'

const STATUS_STYLES = {
  CONFIRMED: 'text-stub-available border-stub-available',
  CANCELLED: 'text-danger border-danger',
  PENDING: 'text-stub-locked border-stub-locked',
  EXPIRED: 'text-paper-muted border-ink-line',
}

export default function MyBookings() {
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [cancellingId, setCancellingId] = useState(null)
  const [downloadingId, setDownloadingId] = useState(null)
  const [downloadError, setDownloadError] = useState(null)

  useEffect(() => {
    load()
  }, [])

  function load() {
    setLoading(true)
    api
      .get('/bookings/my')
      .then(({ data }) => setBookings(data))
      .finally(() => setLoading(false))
  }

  async function handleCancel(id) {
    setCancellingId(id)
    try {
      await api.post(`/bookings/${id}/cancel`)
      load()
    } finally {
      setCancellingId(null)
    }
  }

  // Downloads the invoice/ticket PDF straight from the app - the same PDF
  // that gets emailed after payment, but this doesn't depend on the user
  // actually having access to that email (password/OTP accounts especially).
  async function handleDownloadInvoice(id) {
    setDownloadError(null)
    setDownloadingId(id)
    try {
      const response = await api.get(`/bookings/${id}/invoice`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `StubLine-Invoice-${id}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      setDownloadError(err.response?.data?.message || 'Could not download the invoice. Please try again.')
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-6 py-12">
      <h1 className="font-display text-5xl mb-10">My Bookings</h1>

      {loading && <p className="text-paper-muted">Loading…</p>}

      {!loading && bookings.length === 0 && (
        <div className="card p-10 text-center">
          <p className="text-paper-muted">No bookings yet. Go find something to watch.</p>
        </div>
      )}

      {downloadError && <p className="text-danger text-sm mb-4">{downloadError}</p>}

      <div className="space-y-5">
        {bookings.map((b) => {
          const canDownloadInvoice = b.status === 'CONFIRMED' || b.status === 'CANCELLED'
          return (
            <div key={b.id} className="card p-6 flex justify-between items-start gap-6">
              <div>
                <h2 className="font-display text-2xl mb-1">{b.eventTitle}</h2>
                <p className="text-xs font-mono text-paper-muted mb-3">
                  {new Date(b.eventDate).toLocaleString()}
                </p>
                <p className="text-sm font-mono text-paper-muted mb-1">
                  Seats: {b.seatLabels.join(', ')}
                </p>
                <p className="text-xs font-mono text-paper-muted">Booking ref: #{b.id}</p>
                {b.paymentStatus && (
                  <p className="text-xs font-mono text-paper-muted">
                    Payment: {b.paymentStatus}
                    {b.razorpayPaymentId && <span className="text-paper-muted"> · {b.razorpayPaymentId}</span>}
                  </p>
                )}
              </div>

              <div className="text-right shrink-0">
                <p className={`text-xs font-mono border rounded-full px-3 py-1 inline-block mb-3 ${STATUS_STYLES[b.status]}`}>
                  {b.status}
                </p>
                <p className="font-display text-2xl text-gold mb-3">₹{b.totalAmount}</p>

                <div className="flex flex-col items-end gap-2">
                  {canDownloadInvoice && (
                    <button
                      onClick={() => handleDownloadInvoice(b.id)}
                      disabled={downloadingId === b.id}
                      className="text-xs text-gold hover:underline"
                    >
                      {downloadingId === b.id ? 'Downloading…' : 'Download invoice'}
                    </button>
                  )}
                  {b.status === 'CONFIRMED' && (
                    <button
                      onClick={() => handleCancel(b.id)}
                      disabled={cancellingId === b.id}
                      className="text-xs text-danger hover:underline"
                    >
                      {cancellingId === b.id ? 'Cancelling…' : 'Cancel booking'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
