import { SUPPORT_EMAIL } from '../constants/contact'

const LAST_UPDATED = 'July 2026'

export default function RefundPolicy() {
  return (
    <div className="max-w-3xl mx-auto px-6 py-12">
      <h1 className="font-display text-4xl text-gold mb-2">Refund Policy</h1>
      <p className="text-paper-muted text-sm mb-10">Last updated: {LAST_UPDATED}</p>

      <div className="space-y-8 text-sm leading-relaxed text-paper-muted">
        <Section title="1. Cancelling a booking">
          You can cancel any confirmed booking yourself from{' '}
          <a href="/my-bookings" className="text-gold hover:underline">My Bookings</a>. This
          immediately releases your seat(s) back into availability for other users - no need to
          contact us just to cancel.
        </Section>

        <Section title="2. Getting your money back">
          Cancelling a booking on StubLine is instant, but the refund of the amount you paid is
          currently processed manually by our team rather than automatically. After cancelling,
          email us at{' '}
          <a href={`mailto:${SUPPORT_EMAIL}`} className="text-gold hover:underline">{SUPPORT_EMAIL}</a>{' '}
          with your booking reference number, and we'll process the refund to your original
          payment method via Razorpay within <strong className="text-paper">5-7 business days</strong>.
        </Section>

        <Section title="3. Failed or double payments">
          If a payment fails but money was deducted from your account, Razorpay automatically
          reverses it - this typically shows up within 5-7 business days depending on your bank. If
          it doesn't, contact us with your payment reference and we'll help track it down.
        </Section>

        <Section title="4. Event cancelled or rescheduled by the organizer">
          If an event itself is cancelled or rescheduled, you're entitled to a full refund
          regardless of any cancellation window. We'll email everyone booked for that event with
          next steps.
        </Section>

        <Section title="5. Non-refundable situations">
          We can't refund a booking for an event that has already taken place, or if you simply
          don't show up.
        </Section>

        <Section title="6. Questions about a refund">
          Reach out anytime at{' '}
          <a href={`mailto:${SUPPORT_EMAIL}`} className="text-gold hover:underline">{SUPPORT_EMAIL}</a>{' '}
          with your booking reference, and we'll look into it.
        </Section>
      </div>
    </div>
  )
}

function Section({ title, children }) {
  return (
    <section>
      <h2 className="font-display text-xl text-paper mb-2 tracking-wide">{title}</h2>
      <p>{children}</p>
    </section>
  )
}
