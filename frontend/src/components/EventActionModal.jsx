import { useState } from 'react'

/**
 * Replaces window.prompt() for both cancel (needs a reason) and postpone
 * (needs a new date/time + optional note). The postpone case is the
 * important one: a native <input type="datetime-local"> always emits
 * "YYYY-MM-DDTHH:mm" - exactly what the backend's LocalDateTime
 * deserializer expects - so there's no way to type it in a format the
 * backend can't parse, unlike free-text window.prompt() entry.
 */
export default function EventActionModal({ mode, eventTitle, onCancel, onConfirm }) {
  const [reason, setReason] = useState('')
  const [newDate, setNewDate] = useState('')
  const [note, setNote] = useState('')

  const isPostpone = mode === 'postpone'
  const canSubmit = isPostpone ? newDate.trim().length > 0 : reason.trim().length > 0

  function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    if (isPostpone) {
      onConfirm({ newEventDate: newDate, note: note.trim() || null })
    } else {
      onConfirm({ reason: reason.trim() })
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
      <div className="card p-6 w-full max-w-md">
        <h2 className="font-display text-2xl text-gold mb-1">
          {isPostpone ? 'Postpone event' : 'Cancel event'}
        </h2>
        <p className="text-sm text-paper-muted mb-5">
          "{eventTitle}" - this emails everyone with a confirmed booking.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {isPostpone ? (
            <>
              <label className="block">
                <span className="text-xs text-paper-muted block mb-1">New date &amp; time</span>
                <input
                  type="datetime-local"
                  className="input-field w-full"
                  required
                  value={newDate}
                  onChange={(e) => setNewDate(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-xs text-paper-muted block mb-1">Note to include in the email (optional)</span>
                <textarea
                  className="input-field w-full"
                  rows={2}
                  placeholder="e.g. Rescheduled due to venue availability"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                />
              </label>
            </>
          ) : (
            <label className="block">
              <span className="text-xs text-paper-muted block mb-1">Reason (shown to everyone booked)</span>
              <textarea
                className="input-field w-full"
                rows={3}
                required
                autoFocus
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onCancel} className="btn-secondary !px-4 !py-2 text-sm">
              Never mind
            </button>
            <button
              type="submit"
              disabled={!canSubmit}
              className={`!px-4 !py-2 text-sm ${isPostpone ? 'btn-primary' : 'bg-danger text-paper rounded-md hover:opacity-90 disabled:opacity-50'}`}
            >
              {isPostpone ? 'Postpone' : 'Confirm cancellation'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
