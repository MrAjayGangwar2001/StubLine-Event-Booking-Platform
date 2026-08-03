import { SUPPORT_EMAIL } from '../constants/contact'

const LAST_UPDATED = 'July 2026'

export default function PrivacyPolicy() {
  return (
    <div className="max-w-3xl mx-auto px-6 py-12">
      <h1 className="font-display text-4xl text-gold mb-2">Privacy Policy</h1>
      <p className="text-paper-muted text-sm mb-10">Last updated: {LAST_UPDATED}</p>

      <div className="space-y-8 text-sm leading-relaxed text-paper-muted">
        <Section title="1. What we collect">
          When you create an account: your name and email address (and, if you sign up with
          Google, the name/email Google shares with us). When you book an event: which seats you
          booked, the amount paid, and booking status. We never see or store your card, UPI, or
          bank details - those go directly to Razorpay.
        </Section>

        <Section title="2. How we use it">
          To create and secure your account (including sending OTP codes for login/verification),
          to process bookings and payments, to email you booking confirmations and e-tickets, and
          to show you your own booking history.
        </Section>

        <Section title="3. Third parties we share data with">
          <strong className="text-paper">Razorpay</strong> (payment processing), <strong className="text-paper">Google</strong> (only if
          you use Sign in with Google), and our email provider (to deliver OTP and confirmation
          emails). We don't sell your data to anyone.
        </Section>

        <Section title="4. Cookies and local storage">
          StubLine keeps you signed in using a token stored in your browser's local storage,
          instead of tracking cookies or third-party ad trackers.
        </Section>

        <Section title="5. How long we keep your data">
          We keep your account and booking history for as long as your account is active, so you
          can view past bookings and re-download tickets. You can request deletion at any time (see
          below).
        </Section>

        <Section title="6. Your rights">
          You can update your name and password anytime from your Profile page. To request a copy
          of your data or to delete your account, email us at{' '}
          <a href={`mailto:${SUPPORT_EMAIL}`} className="text-gold hover:underline">{SUPPORT_EMAIL}</a>.
        </Section>

        <Section title="7. Children's privacy">
          StubLine isn't directed at children under 13, and we don't knowingly collect data from
          them.
        </Section>

        <Section title="8. Changes to this policy">
          If we make material changes to how we handle your data, we'll update this page and, where
          appropriate, notify you by email.
        </Section>

        <Section title="9. Contact">
          Privacy questions? Email{' '}
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
