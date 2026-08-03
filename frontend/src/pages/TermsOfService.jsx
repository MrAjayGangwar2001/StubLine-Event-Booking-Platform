import { SUPPORT_EMAIL } from '../constants/contact'

const LAST_UPDATED = 'July 2026'

export default function TermsOfService() {
  return (
    <div className="max-w-3xl mx-auto px-6 py-12">
      <h1 className="font-display text-4xl text-gold mb-2">Terms of Service</h1>
      <p className="text-paper-muted text-sm mb-10">Last updated: {LAST_UPDATED}</p>

      <div className="space-y-8 text-sm leading-relaxed text-paper-muted">
        <Section title="1. Acceptance of terms">
          By creating an account or booking an event through StubLine, you agree to these Terms
          of Service. If you don't agree with any part of these terms, please don't use the
          platform.
        </Section>

        <Section title="2. Your account">
          You can create an account using an email + password, email OTP, or Google Sign-In. You're
          responsible for keeping your login credentials secure and for all activity under your
          account. Let us know immediately if you suspect unauthorized access.
        </Section>

        <Section title="3. Booking and seat holds">
          When you select a seat, it's held for you for a limited window (shown as a countdown
          timer) while you complete payment. If payment isn't completed before the hold expires,
          the seat is released and may be booked by someone else. A booking is only confirmed once
          payment succeeds and you receive a confirmation email with your e-ticket.
        </Section>

        <Section title="4. Payments">
          All payments are processed securely through Razorpay. StubLine does not store your card,
          UPI, or bank account details. Prices are shown in Indian Rupees (INR) and are set by
          event organizers/administrators.
        </Section>

        <Section title="5. Cancellations and refunds">
          You can cancel a confirmed booking from the "My Bookings" page, which immediately
          releases your seat(s) back into availability. See our{' '}
          <a href="/refund-policy" className="text-gold hover:underline">Refund Policy</a> for how
          refunds of the paid amount are handled.
        </Section>

        <Section title="6. Acceptable use">
          You agree not to: use bots or automation to book seats, resell tickets at a markup
          outside the platform, attempt to bypass seat-locking or payment mechanisms, or use the
          platform for any unlawful purpose.
        </Section>

        <Section title="7. Event changes and cancellations by organizers">
          Event dates, venues, and lineups are set by organizers/administrators and may change.
          Where an event itself is cancelled or postponed, we'll do our best to notify booked
          attendees by email.
        </Section>

        <Section title="8. Limitation of liability">
          StubLine is provided "as is." We aren't liable for losses arising from event
          cancellations by organizers, third-party payment gateway issues, or circumstances outside
          our reasonable control.
        </Section>

        <Section title="9. Changes to these terms">
          We may update these terms from time to time. Continued use of StubLine after changes are
          posted means you accept the revised terms.
        </Section>

        <Section title="10. Contact">
          Questions about these terms? Reach us at{' '}
          <a href={`mailto:${SUPPORT_EMAIL}`} className="text-gold hover:underline">{SUPPORT_EMAIL}</a>.
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
