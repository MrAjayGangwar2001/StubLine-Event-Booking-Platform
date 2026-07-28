# 🎟️ StubLine — Real-Time Event Booking & Payment Platform

A full-stack event booking system that solves the classic "50 people click the
same seat at once" problem — with a complete admin operations layer built on
top: event lifecycle management, a role hierarchy, live analytics, and more.

Built solo, end to end (backend, frontend, infra, and every bug fixed along
the way is documented, not hidden).

**[Live Demo](#)** &nbsp;·&nbsp; **[Full Technical Documentation](./StubLine-Project-Documentation.md)** (architecture, every design decision explained, interview Q&A)

---

## The Core Problem

What happens when 50 people click the same seat, on the same event, at
exactly the same moment? Only one should win — and everyone else should see
"sorry, taken" *instantly*, not after they've already entered payment
details.

**Solved with:**
- **Redis distributed locking (`SETNX`)** — atomic, so exactly one request
  ever wins, even under real concurrent load
- **WebSocket/STOMP live updates** — every other viewer sees the seat flip
  to "locked" the instant someone else takes it, no polling
- **Optimistic locking (JPA `@Version`)** as a second, independent line of
  defense at the actual database write

---

## Features

**Booking & Payments**
- Real-time seat map with live lock status across all connected clients
- Razorpay integration — HMAC-SHA256 signature verified **server-side**,
  never trusting a client's claim of success
- A **webhook** as an independent backup confirmation path — if a user's
  browser drops connection right after paying, the booking still gets
  confirmed instead of staying stuck `PENDING` forever
- Free (₹0) events/seats confirm instantly, bypassing the payment gateway
  entirely (Razorpay rejects 0-amount orders)
- Async ticket generation (PDF + QR code) via Kafka, decoupled from the
  checkout request
- In-app invoice download — not just an emailed copy

**Auth**
- Email + password, email OTP (passwordless), and Google Sign-In
- OTP requests/attempts are rate-limited (per email + purpose)
- A Google-only account can still set a password later and log in from any
  device, without needing an active Google session

**Admin Operations**
- Event lifecycle controls: pause/resume booking, cancel (auto-notifies
  everyone booked), postpone (same, with old/new date)
- **Role hierarchy** (`USER` / `ADMIN` / `SUPER_ADMIN`) — only a super admin
  can create more admins, preventing an unlimited promotion chain
- Homepage announcement banner (severity levels, admin-managed)
- Visitor analytics, revenue/occupancy dashboards

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 3.3 · Spring Security (JWT) · Spring Data JPA |
| Database | MySQL 8 |
| Real-time | Redis 7 (distributed locks) · WebSocket/STOMP |
| Async | Apache Kafka |
| Payments | Razorpay REST API (no SDK) + webhook, HMAC-verified |
| PDF / QR | Apache PDFBox · ZXing |
| Frontend | React 18 · Vite · Tailwind CSS · Axios |
| Testing | JUnit 5 · Mockito · Testcontainers |
| Infra | Docker Compose · GitHub Actions CI |

Full breakdown of *why* each technology was chosen (and what the alternative
would have been) is in the [technical documentation](./StubLine-Project-Documentation.md).

---

## Architecture

```
React (Vite) SPA  ──HTTP/REST──▶  Spring Boot API  ──▶  MySQL
       │                              │
       └──WebSocket/STOMP──▶          ├──▶  Redis (seat locks)
                                       │
                                       ├──▶  Kafka ──▶ Consumer ──▶ PDF + Email
                                       │
                                       ├──▶  Razorpay (payments, outbound)
                                       └──◀  Razorpay (webhook, inbound)
```

---

## Getting Started

### Prerequisites
- Docker + Docker Compose
- (Optional, for live payments/email/Google login) Razorpay test-mode keys,
  a Gmail App Password, and a Google OAuth client ID — the app runs without
  these, just with payments/email/Google-login disabled

### Run locally

```bash
git clone https://github.com/MrAjayGangwar2001/StubLine-Event-Booking-Platform.git
cd StubLine-Event-Booking-Platform
cp .env.example .env
# fill in .env with your own keys — see comments in the file for where to get each one
docker compose up --build
```

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080/api`
- A default `SUPER_ADMIN` account is seeded on first run
- 
### Environment variables

See `.env.example` for the full list with comments on where to get each
value. Key ones:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signs auth tokens |
| `RAZORPAY_KEY_ID` / `_KEY_SECRET` / `_WEBHOOK_SECRET` | Payment gateway |
| `GOOGLE_CLIENT_ID` | Google Sign-In |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP (OTP + booking emails) |
| `VITE_API_URL` / `VITE_WS_URL` | Baked into the frontend build — must point to wherever a browser can reach the backend |

---

## Known Limitations

Being upfront about these rather than hiding them:

- Refunds are a **manual** process — cancelling releases the seat instantly,
  but no automated Razorpay Refunds API call is wired up yet.
- Poster images and ticket PDFs are stored on local disk (persisted via
  Docker volumes) — fine for a single-VM deployment, would need Google Cloud
  Storage (or S3) for a stateless/multi-instance deployment.
- No rate limiting on the booking API itself (only on OTP requests).
- Visitor tracking counts browser sessions, not deduplicated unique
  visitors.

More detail — including bugs found and fixed along the way, and the
reasoning behind every major design decision — in the
[full technical documentation](./StubLine-Project-Documentation.md).

---

## License

MIT
