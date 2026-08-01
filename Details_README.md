# Real-Time Event Booking & Payment Platform

A full-stack event booking system with concurrent seat locking, real-time
availability updates, and async payment confirmation — built to demonstrate
production-style backend engineering, not just CRUD.

## Post-Launch Hardening — Email OTP Verification + Google Sign-In ✅

The original auth flow let anyone register with any email, verified or not,
and log straight in. This closes that gap and adds a passwordless option:

- [x] **Email OTP verification on signup** — registering no longer returns a
      usable session; a 6-digit code is emailed, and the account can't log
      in at all (enforced via `User.isEnabled()`, tied directly into Spring
      Security's standard auth flow) until it's verified
- [x] **OTP-based login** — an already-verified user can log in with a fresh
      emailed code instead of their password, as an alternative on the login screen
- [x] **Google Sign-In** — via Google Identity Services (client-side ID
      token), verified server-side against Google's public keys before
      trusting any claim in it. A Google sign-in auto-verifies the account
      (Google already confirmed the email) and links to an existing account
      if that email previously registered with a password
- [x] Unit tests covering: unverified-account login rejection, Google-account
      password-login rejection, new-account creation via Google, and OTP
      issuance on registration

### Honest scope notes
- **OTP emails are mocked** (logged, not delivered) — same as booking
  confirmations, since there's no SMTP configured. Unlike booking
  confirmations, this means the *only* way to actually use signup or
  OTP-login right now is reading the code out of backend logs:
  ```bash
  docker compose logs backend | Select-String "MOCK EMAIL - OTP CODE" -Context 0,5
  ```
  (PowerShell; use `grep -A5` on Mac/Linux)
- **No rate-limiting or brute-force lockout on OTP attempts.** A 6-digit
  code is 1-in-a-million per guess, but nothing currently throttles repeated
  attempts against one email. Flagged as a real gap for production, not
  silently glossed over - see `AuthService`'s class comment.
- **Google client secret is never used.** Only the client ID is needed
  server-side (as the token "audience" to verify against) - Google Identity
  Services' client-side token flow doesn't need the secret at all, unlike
  the older server-side OAuth2 redirect flow. Nothing secret-shaped exists
  in this repo's Google config.

### Google Sign-In setup
1. [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials → **Create OAuth client ID** → Web application
2. Under **Authorized JavaScript origins**, add your frontend URL (e.g. `http://localhost:5173`)
3. Set `GOOGLE_CLIENT_ID` in your `.env` file (used by both the backend, for
   token verification, and the frontend build, for rendering the button)

### New API endpoints
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/verify-signup-otp` | Public | Verify signup code, returns JWT |
| POST | `/api/auth/login/otp/request` | Public | Email a login code to an already-verified account |
| POST | `/api/auth/login/otp/verify` | Public | Verify login code, returns JWT |
| POST | `/api/auth/google` | Public | Verify a Google ID token, returns JWT |

`POST /api/auth/register` now returns `{ email, message }` instead of a JWT -
the frontend routes to `/verify-otp` next, not straight into the app.



## Status: Week 6 of 6 — Dockerization, CI/CD, AWS Deployment ✅ (Project Complete)

- [x] All Week 1-5 features (auth, real-time seat locking, payments, async
      confirmation, Testcontainers integration tests, admin analytics)
- [x] **Frontend Dockerized** — multi-stage build (Vite build → nginx serve),
      with SPA client-side routing support (`try_files` fallback) and gzip'd,
      cache-forever hashed assets
- [x] **Full `docker-compose.yml`** — MySQL, Redis, Kafka+Zookeeper, backend,
      and frontend all wired together with proper health-check-based startup
      ordering (frontend waits for backend's `/actuator/health` to pass, not
      just for the container to start)
- [x] **GitHub Actions CI/CD** (`.github/workflows/ci.yml`):
  - Backend unit tests (fast, no Docker)
  - Backend integration tests (Testcontainers, needs Docker - GitHub-hosted
    runners provide it natively)
  - Frontend build validation
  - Docker image builds for both services
  - Conditional AWS EC2 deploy job - runs only if deployment secrets are
    configured, otherwise skips itself with a clear notice rather than
    failing the pipeline
- [x] **AWS deployment guide** (`DEPLOYMENT.md`) — two documented paths: a
      simple single-EC2 Docker Compose setup (what the CI deploy job
      targets), and a more production-shaped S3 + CloudFront + RDS split,
      including the concrete next step for moving ticket PDFs to S3
- [x] `.env.example` — every configurable variable documented in one place
- [x] `.dockerignore` for both services

### Running the full stack locally
```bash
cp .env.example .env
# edit .env: at minimum set JWT_SECRET and (optionally) Razorpay test keys
docker compose up -d --build
```
Frontend: `http://localhost:5173` · Backend: `http://localhost:8080/api` ·
Backend health: `http://localhost:8080/actuator/health`

### Deploying to AWS
See [`DEPLOYMENT.md`](./DEPLOYMENT.md) for the full walkthrough (EC2 setup,
security groups, optional CI auto-deploy via SSH, and the S3/CloudFront path).

### Running the integration tests
Needs Docker running locally (Testcontainers starts real containers):
```bash
cd backend
mvn test -Dtest=*IT
```
Regular unit tests (`mvn test` without the filter) don't need Docker at all -
only the three `*IT` classes in `com.eventbooking.integration` do.

### Analytics endpoints (admin-only)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/admin/analytics/summary` | Total revenue, confirmed/pending bookings, event counts, overall occupancy % |
| GET | `/api/admin/analytics/events` | Per-event revenue + occupancy breakdown |
| GET | `/api/admin/analytics/bookings-timeline?daysBack=30` | Daily confirmed-booking counts + revenue for the chart |

### Payment flow, end to end
1. `POST /api/bookings` — validates the caller holds the seat lock, creates a
   `PENDING` booking, creates a Razorpay order for the exact seat total, and
   extends the Redis hold to cover the payment window (default 10 min)
2. Frontend opens Razorpay Checkout with the returned order id
3. User pays (test mode - no real money moves) → Razorpay calls back with a payment id + signature
4. `POST /api/bookings/verify-payment` — **re-computes the HMAC signature server-side**
   and only trusts it if it matches; never trusts the client's word alone
5. On success: seats flip to `BOOKED` (same `@Version` + Redis-lock double-defense
   as Week 3), booking becomes `CONFIRMED`, and a `BookingConfirmedEvent` publishes to Kafka
6. A separate consumer (different thread, fully decoupled from the checkout request)
   generates a PDF ticket with a QR code and logs a "sent" confirmation email
7. If the user closes the checkout modal instead of paying, `POST /api/bookings/{id}/cancel-pending`
   releases the hold immediately; if they just abandon the tab, the scheduled
   cleanup job catches it a couple of minutes after the payment window lapses

### Honest scope notes
- **Email sending is mocked** (logged, not actually delivered) — real delivery
  needs SMTP/SES credentials this project doesn't have configured. Swapping in
  real delivery only means reimplementing `EmailService`'s one method; nothing
  else in the Kafka consumer needs to change.
- **No refund flow.** Cancelling a `CONFIRMED` booking releases the seat but
  doesn't call Razorpay's Refunds API - noted as a real gap, not something
  silently skipped.
- **QR code encodes only the booking id**, not a signed verification token -
  there's no venue-scanner app in this project to actually validate it against
  forgery, so a cryptographically signed payload would be complexity without
  a consumer.
- Requires **free Razorpay test-mode keys** to actually run the payment step -
  see setup below.



## Tech Stack

| Layer | Tech |
|---|---|
| Backend | Spring Boot 3.3, Spring Security (JWT), Spring Data JPA |
| DB | MySQL 8 |
| Real-time (Week 3+) | Redis, WebSocket (STOMP) |
| Async (Week 4+) | Kafka |
| Payments (Week 4+) | Razorpay/Stripe |
| Frontend (Week 2+) | React, Tailwind/Material UI |
| Testing | JUnit 5, Mockito, Testcontainers, Awaitility |
| DevOps | Docker, Docker Compose, AWS EC2/S3 |

## Local Setup

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- MySQL 8 (or use the Docker Compose service below)

### Option A — Run everything via Docker Compose
```bash
docker compose up --build
```
This starts MySQL, Redis, Kafka+Zookeeper, and the backend together.
API will be available at `http://localhost:8080`.

### Option B — Run backend locally against Dockerized MySQL
```bash
# Start just the DB
docker compose up -d mysql

# Run the Spring Boot app
cd backend
mvn spring-boot:run
```

### Default Admin Account (seeded on first startup)
```
email:    admin@xxxxxxxx
password: xxxxxxxxx
```

## API Endpoints (Full API surface)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register new user (unverified, no JWT yet - see below) |
| POST | `/api/auth/verify-signup-otp` | Public | Verify signup code, returns JWT |
| POST | `/api/auth/login` | Public | Login with password, returns JWT |
| POST | `/api/auth/login/otp/request` | Public | Email a login code to a verified account |
| POST | `/api/auth/login/otp/verify` | Public | Verify login code, returns JWT |
| POST | `/api/auth/google` | Public | Verify a Google ID token, returns JWT |
| GET | `/api/venues` | Public | List all venues |
| GET | `/api/venues/{id}` | Public | Get venue by id |
| POST | `/api/venues` | ADMIN | Create venue |
| POST | `/api/venues/{id}/seats/generate` | ADMIN | Generate physical seat layout (rows × tiers) |
| GET | `/api/venues/{id}/seats` | Public | List a venue's physical seats |
| GET | `/api/events` | Public | List upcoming events (optional `?category=`) |
| GET | `/api/events/{id}` | Public | Get event by id |
| POST | `/api/events` | ADMIN | Create event |
| GET | `/api/events/admin/all` | ADMIN | Every event regardless of status/date (public listing only shows bookable ones) |
| POST | `/api/events/{id}/seats/generate` | ADMIN | Price venue seats for this event, put on sale |
| GET | `/api/events/{id}/seats` | Public | Seat map with live status (the booking page's data source) |
| POST | `/api/events/{eventId}/seats/{seatId}/lock` | USER | Acquire a 5-minute hold on a seat before booking |
| DELETE | `/api/events/{eventId}/seats/{seatId}/lock` | USER | Release a held seat early (deselect) |
| POST | `/api/bookings` | USER | Create a PENDING booking + Razorpay order |
| POST | `/api/bookings/verify-payment` | USER | Verify payment signature, confirm booking + book seats |
| POST | `/api/bookings/{id}/cancel-pending` | USER | Cancel a PENDING booking before paying, releases the hold |
| GET | `/api/bookings/my` | USER | Current user's booking history |
| POST | `/api/bookings/{id}/cancel` | USER | Cancel an already-confirmed booking, releases seats (no refund - see scope notes) |
| GET | `/api/admin/analytics/summary` | ADMIN | Total revenue, confirmed/pending bookings, event counts, overall occupancy % |
| GET | `/api/admin/analytics/events` | ADMIN | Per-event revenue + occupancy breakdown |
| GET | `/api/admin/analytics/bookings-timeline?daysBack=30` | ADMIN | Daily confirmed-booking counts + revenue for the chart |
| GET | `/actuator/health` | Public | Health check (used by Docker Compose + load balancers) |

**WebSocket**: connect to `/ws` (SockJS), subscribe to `/topic/event/{eventId}` for live `{eventSeatId, status, lockTtlSeconds}` updates.

### Razorpay test-mode setup (required for the payment step to work)
1. Sign up free at https://dashboard.razorpay.com and toggle **Test Mode** (top right)
2. Go to Settings → API Keys → generate a test key pair
3. Set env vars before starting the backend:
   ```bash
   export RAZORPAY_KEY_ID=rzp_test_xxxxxxxx
   export RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxx
   ```
4. Use [Razorpay's test card numbers](https://razorpay.com/docs/payments/payments/test-card-details/) at checkout - no real money moves in test mode

All authenticated requests need header: `Authorization: Bearer <token>`

### Setting up an event end-to-end (admin flow)
1. `POST /api/venues` — create the venue
2. `POST /api/venues/{id}/seats/generate` — define its physical row/seat/tier layout (done once, reused by every event at that venue)
3. `POST /api/events` — create the event at that venue
4. `POST /api/events/{id}/seats/generate` — set a price per tier, this creates one bookable `EventSeat` per physical seat

The Admin page in the frontend walks through steps 1-2 and 3-4 as two guided forms.

## Frontend Setup

```bash
cd frontend
npm install
npm run dev
```
Runs at `http://localhost:5173`, proxying API calls to `http://localhost:8080/api`
(override with a `VITE_API_URL` env var if needed).

## Running Tests
```bash
cd backend
mvn test                # unit tests only - fast, no Docker needed
mvn test -Dtest=*IT     # + integration tests - needs Docker running (Testcontainers)
```

## Architecture (as built)

```
React (WebSocket client + REST calls)
        │
        ▼
Spring Boot API ──────► MySQL (bookings, events, users)
        │
        ├──► Redis (seat locks, cache)
        │
        ├──► WebSocket (live seat status broadcast)
        │
        └──► Kafka ──► Consumer Service ──► Email + PDF ticket
        │
        └──► Payment Gateway (Razorpay/Stripe, webhook)
```
