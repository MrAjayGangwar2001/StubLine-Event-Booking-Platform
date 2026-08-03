const TIER_LABEL = {
  GOLD: 'Gold',
  SILVER: 'Silver',
  PLATINUM: 'Platinum',
}

function groupByRow(seats) {
  const rows = {}
  for (const seat of seats) {
    if (!rows[seat.rowLabel]) rows[seat.rowLabel] = []
    rows[seat.rowLabel].push(seat)
  }
  return Object.entries(rows).sort(([a], [b]) => a.localeCompare(b))
}

function seatClasses(seat, isSelected) {
  if (seat.status === 'BOOKED') {
    return 'bg-stub-booked text-paper-muted cursor-not-allowed border-transparent'
  }
  if (isSelected) {
    // Locked by ME (I hold the Redis lock) - actively selected for checkout
    return 'bg-stub-selected text-ink border-stub-selected font-semibold scale-105'
  }
  if (seat.status === 'LOCKED') {
    // Locked by someone ELSE right now - can't be selected until it expires or they release it
    return 'bg-stub-locked/20 text-stub-locked border-stub-locked cursor-not-allowed animate-pulse'
  }
  // AVAILABLE
  return 'bg-transparent border-stub-available text-stub-available hover:bg-stub-available/20 cursor-pointer'
}

export default function SeatMap({ seats, selectedIds, onToggleSeat, lockCountdowns }) {
  if (!seats || seats.length === 0) {
    return <p className="text-paper-muted text-sm">No seats configured for this event yet.</p>
  }

  const rows = groupByRow(seats)

  return (
    <div className="space-y-3">
      {/* Screen / stage indicator */}
      <div className="mb-8">
        <div className="h-2 bg-gradient-to-r from-transparent via-gold-dim to-transparent rounded-full" />
        <p className="text-center text-xs text-paper-muted mt-2 tracking-widest uppercase">Stage</p>
      </div>

      {rows.map(([rowLabel, rowSeats]) => (
        <div key={rowLabel} className="flex items-center gap-3">
          <span className="w-5 text-xs font-mono text-paper-muted">{rowLabel}</span>
          <div className="flex flex-wrap gap-2">
            {rowSeats
              .sort((a, b) => a.seatNumber - b.seatNumber)
              .map((seat) => {
                const isSelected = selectedIds.includes(seat.id)
                const isDisabled = seat.status === 'BOOKED' || (seat.status === 'LOCKED' && !isSelected)
                const secondsLeft = isSelected ? lockCountdowns?.[seat.id] : null
                return (
                  <button
                    key={seat.id}
                    disabled={isDisabled}
                    onClick={() => onToggleSeat(seat)}
                    title={
                      seat.status === 'LOCKED' && !isSelected
                        ? `${seat.rowLabel}${seat.seatNumber} · being held by another user`
                        : `${seat.rowLabel}${seat.seatNumber} · ${TIER_LABEL[seat.tier]} · ₹${seat.price}`
                    }
                    className={`relative w-9 h-9 rounded-md border text-[11px] font-mono flex items-center justify-center
                      transition-all ${seatClasses(seat, isSelected)}`}
                  >
                    {seat.seatNumber}
                    {secondsLeft != null && secondsLeft > 0 && (
                      <span className="absolute -top-2 -right-2 bg-ink text-gold text-[9px] rounded-full w-4 h-4 flex items-center justify-center border border-gold">
                        {Math.ceil(secondsLeft / 60)}
                      </span>
                    )}
                  </button>
                )
              })}
          </div>
        </div>
      ))}

      {/* Legend */}
      <div className="flex flex-wrap gap-5 pt-6 mt-6 border-t border-ink-line text-xs text-paper-muted">
        <LegendItem colorClass="border-stub-available" label="Available" />
        <LegendItem colorClass="bg-stub-selected border-stub-selected" label="Your selection" filled />
        <LegendItem colorClass="border-stub-locked" label="Held by another user" />
        <LegendItem colorClass="bg-stub-booked border-transparent" label="Booked" filled />
      </div>
    </div>
  )
}

function LegendItem({ colorClass, label, filled }) {
  return (
    <div className="flex items-center gap-2">
      <span className={`w-4 h-4 rounded border ${filled ? colorClass : `bg-transparent ${colorClass}`}`} />
      <span>{label}</span>
    </div>
  )
}
