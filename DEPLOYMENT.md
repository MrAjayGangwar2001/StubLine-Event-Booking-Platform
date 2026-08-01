# Deployment Guide

Two paths are documented here:

- **Path A (simple)** — everything (backend + frontend + MySQL + Redis + Kafka)
  on a single EC2 instance via Docker Compose. Fastest to stand up, good for
  a portfolio demo link, matches what `.github/workflows/ci.yml`'s `deploy`
  job automates.
- **Path B (more production-shaped)** — frontend on S3 + CloudFront, backend
  on EC2, MySQL on RDS. More moving parts, closer to how a real team would
  actually split this up. Documented as manual steps, not automated by the
  CI workflow in this repo (intentionally - automating infra provisioning
  belongs in Terraform/CloudFormation, not a shell script in a GitHub Action).

---

## Path A: Single EC2 instance (Docker Compose)

### 1. Launch the instance
- EC2 → Launch Instance → **Ubuntu 22.04 LTS**, `t3.medium` or larger
  (t3.small can work but Kafka+MySQL+Redis+2 JVMs is tight on 2GB RAM)
- Security group inbound rules:
  | Port | Source | Purpose |
  |---|---|---|
  | 22 | your IP only | SSH |
  | 8080 | 0.0.0.0/0 | Backend API |
  | 5173 | 0.0.0.0/0 | Frontend |
- Create/attach an SSH key pair - you'll need the `.pem` file

### 2. Install Docker on the instance
```bash
ssh -i your-key.pem ubuntu@<EC2_PUBLIC_IP>

sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
sudo usermod -aG docker $USER
newgrp docker   # or log out/in for the group change to apply
```

### 3. Clone the repo and configure environment
```bash
git clone <your-repo-url> event-booking-platform
cd event-booking-platform
cp .env.example .env
nano .env   # fill in JWT_SECRET, RAZORPAY keys, and set URLs to your EC2 public IP/domain
```
Set `FRONTEND_URL`, `VITE_API_URL`, `VITE_WS_URL` in `.env` to use your EC2
public IP (or domain, once you have one) instead of `localhost` - the
frontend build bakes these in, so a browser needs to actually be able to
reach that address.

### 4. Start everything
```bash
docker compose up -d --build
docker compose logs -f backend   # watch startup, confirm no errors
```
Visit `http://<EC2_PUBLIC_IP>:5173`.

### 5. (Optional) Set up CI/CD auto-deploy
To make `git push` to `main` automatically redeploy here:
1. In your GitHub repo → Settings → Secrets and variables → Actions, add:
   - `EC2_HOST` — the instance's public IP or domain
   - `EC2_USER` — usually `ubuntu`
   - `EC2_SSH_KEY` — the **private** key content (the `.pem` file), not the public key
2. Push to `main` - the `deploy` job in `ci.yml` will SSH in, `git pull`, and
   `docker compose up -d --build` automatically. Without those secrets set,
   that job just logs a notice and skips itself - it won't fail your build.

### 6. (Optional) HTTPS via a domain + Let's Encrypt
Point a domain's A record at the EC2 IP, then front the frontend container
with Certbot + nginx (or swap the frontend's nginx.conf for a version that
terminates TLS directly) - not included by default since it needs a real
domain name to set up, which this project doesn't assume you have yet.

---

## Path B: S3 + CloudFront (frontend) + EC2 (backend) + RDS (MySQL)

This is closer to what the resume bullet "AWS EC2 / S3 / CloudFront" refers
to for the other portfolio projects, and is a reasonable next step once the
simple EC2-everything setup is working.

### Frontend → S3 + CloudFront
```bash
cd frontend
VITE_API_URL=https://api.yourdomain.com/api \
VITE_WS_URL=https://api.yourdomain.com/ws \
npm run build

aws s3 sync dist/ s3://your-bucket-name --delete
```
- Create the S3 bucket with **static website hosting** enabled, or (better)
  keep it private and put **CloudFront** in front of it with an Origin Access
  Control - avoids the bucket itself needing to be public.
- CloudFront distribution: set the **default root object** to `index.html`,
  and add a **custom error response** mapping 403/404 → `/index.html` with a
  200 status - this is what makes React Router's client-side routes (e.g.
  `/events/5`) work on a hard refresh, same purpose as the `try_files` line
  in `nginx.conf` for Path A.

### Backend → EC2 (same as Path A, minus the frontend container)
Run `docker compose up -d --build mysql redis kafka zookeeper backend` on the
EC2 instance (skip the `frontend` service since CloudFront serves it now).

### Database → RDS instead of the MySQL container
- Create an RDS MySQL 8 instance, note its endpoint
- Update the backend's env vars: `DB_HOST=<rds-endpoint>`, real `DB_USER`/`DB_PASSWORD`
  (don't keep using `root`/`root` against a real database)
- Security group: allow inbound 3306 from the EC2 instance's security group specifically,
  not `0.0.0.0/0`

### Ticket PDFs → S3 instead of local disk
`TicketService` currently writes to `app.tickets.output-dir` on local disk
(fine for a single EC2 instance, but doesn't survive the instance being
replaced, and doesn't scale to multiple backend instances). Swapping to S3:
1. Add the AWS SDK (`software.amazon.awssdk:s3`) to `pom.xml`
2. In `TicketService.generateTicketPdf()`, after writing the PDF, upload it
   with `s3Client.putObject(...)` instead of (or in addition to) keeping the
   local copy
3. Update `EmailService` to link to the S3 object (a presigned URL, not a
   public one) instead of a local file path

This isn't implemented in this repo yet - noted here as the concrete next
step, not silently skipped.

---

## Environment variables reference

See `.env.example` for the full list. The ones that matter most for a
production-like deployment (not just local dev):

| Variable | Why it matters in production |
|---|---|
| `JWT_SECRET` | Must be a long, random, secret value - never the default placeholder |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | Switch to **live mode** keys only when actually ready to accept real payments |
| `DB_PASSWORD` | Never `root`/`root` against anything internet-reachable |
| `FRONTEND_URL` | Used for CORS - must exactly match the frontend's real origin |
