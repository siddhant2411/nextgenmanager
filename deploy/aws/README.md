# Deploying NextGenManager for a customer on AWS

One customer, one EC2 instance, with the database and files on AWS managed services.

```
                       ┌──────────────── EC2 (t3.medium) ───────────────┐
browser ──HTTPS:443──> │ caddy ──/api/*──> backend :8080 ───────────────┼──> RDS PostgreSQL (private)
                       │   └────────────> frontend :80 (nginx + React)  │
                       └────────────────────────────────────────────────┘──> S3 bucket (attachments)
```

**Why this shape:**
- **Same origin.** The UI and the API sit behind one domain. There's no CORS to configure, there's one certificate, and only ports 80 and 443 are open.
- **No data on the instance.** Accounting data lives in RDS, which takes automated daily backups and supports point-in-time restore. Attachments live in S3. If the EC2 instance dies you rebuild it in 20 minutes and lose nothing.
- **S3, not a MinIO container.** The app hands the browser presigned links built from `MINIO_URL`. A MinIO container's address (`http://minio:9000`) can't be reached from a customer's browser. S3's links can.
- **Caddy** obtains and renews the Let's Encrypt certificate itself.

Rough cost in Mumbai (ap-south-1), on-demand: EC2 t3.medium ~$30/mo, RDS db.t4g.small + 20 GB ~$30/mo, plus public IP, S3 and backups ~$5/mo. That's **about $65/month**. It drops to ~$45 with db.t4g.micro for a small team (≤10 users), and roughly 35–40% lower with a 1-year reserved instance or savings plan.

---

## 1. Region and account

- Use **ap-south-1 (Mumbai)**. It keeps the customer's data in India and gives the lowest latency.
- Use a separate AWS account (or at least separate resources, all tagged `customer=<name>`) per customer, so you can bill them and hand the setup over cleanly.
- Turn on MFA for the root user and work as an IAM user/role. Set a **billing alarm** (Billing → Budgets) at around $100.

## 2. Database: RDS PostgreSQL

RDS → Create database:

| Setting | Value |
|---|---|
| Engine | PostgreSQL 16 |
| Template | Production (or Dev/Test for a pilot) |
| Instance | `db.t4g.small` (`db.t4g.micro` for ≤10 users) |
| Storage | gp3, 20 GB, autoscaling on, max 100 GB |
| DB name | `nextgenmanager` |
| Master user | `ngm_admin` (keep this password in your password manager) |
| Public access | **No** |
| VPC security group | new: `ngm-rds`, no inbound rules yet |
| Backup retention | **14 days** |
| Deletion protection | **On** |
| Encryption | On (default) |

After it's created:

1. Add an inbound rule to `ngm-rds`: PostgreSQL 5432, source = the EC2 security group `ngm-app` from step 4.
2. Create a non-master user for the application (from the EC2 instance later, using `psql`):
   ```sql
   CREATE USER ngm_app WITH PASSWORD '<strong password>';
   GRANT ALL ON DATABASE nextgenmanager TO ngm_app;
   \c nextgenmanager
   GRANT ALL ON SCHEMA public TO ngm_app;
   ```

**Leave the database empty.** Flyway builds the whole schema on first start (V1 → latest), including roles, the admin user, the chart of accounts and the TDS sections.

## 3. File storage: S3 bucket and a scoped key

1. S3 → Create bucket `ngm-<customer>-files` in ap-south-1. Keep **Block all public access** on. The app only ever gives out time-limited presigned links. Turn on **Versioning**, so an overwritten or deleted attachment can be recovered.
2. IAM → Users → create `ngm-<customer>-app` with no console access. Attach this inline policy:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": ["s3:ListBucket", "s3:GetBucketLocation"],
         "Resource": "arn:aws:s3:::ngm-<customer>-files" },
       { "Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::ngm-<customer>-files/*" }
     ]
   }
   ```
3. Create an access key for that user. It goes in `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`.

## 4. Server: EC2

EC2 → Launch instance:

| Setting | Value |
|---|---|
| AMI | Ubuntu Server 24.04 LTS |
| Type | `t3.medium` (2 vCPU / 4 GB) |
| Storage | 30 GB gp3 |
| Security group | new: `ngm-app`. Inbound: 80 and 443 from anywhere; 22 from **your IP only** |
| IAM role | `AmazonSSMManagedInstanceCore` (lets you use Session Manager instead of SSH) |

Then:
1. **Elastic IPs** → allocate one and associate it with the instance.
2. In the customer's DNS, add an **A record**, e.g. `erp.customer.com → <Elastic IP>`. Do this *before* first start, because Caddy needs it to get the certificate.

Install Docker and add swap (the React build needs more than 4 GB of memory on its own):

```bash
sudo apt update && sudo apt -y upgrade
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && newgrp docker
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
sudo apt -y install postgresql-client git
```

## 5. Code and configuration

```bash
mkdir -p ~/ngm && cd ~/ngm
git clone <backend repo> nextgenmanager      # use a read-only deploy key
git clone git@github.com:siddhant2411/nextgenmanagerui.git ui
cd nextgenmanager/deploy/aws
cp .env.example .env && chmod 600 .env
nano .env                                     # fill in every value
mkdir -p config
```

Generate the secrets with `openssl rand -base64 64 | tr -d '\n'`, one for `JWT_SECRET` and one for `RECOVERY_SECRET`. **Do not reuse secrets across customers.**

**User agreement:** copy `src/main/resources/agreement/user-agreement.html` to `config/user-agreement.html`, fill in the customer's names (all the `[BRACKETED]` placeholders), and set `APP_AGREEMENT_LOCATION=file:/config/user-agreement.html` in `.env`. Have a lawyer review the template once before the first customer.

## 6. First start

```bash
docker compose up -d --build        # first build takes ~10 minutes
docker compose logs -f backend      # wait for "Started NextgenmanagerApplication"
```

Flyway runs every migration on the empty database during this first start.

Open `https://erp.customer.com`, then:

1. Sign in as the seeded `admin` user. You'll get the user agreement first; accept it.
2. **Change the admin password at once** (Account settings). The seeded password is in the public repo history.
3. Create the company profile and the **financial year** (Accounting → Financial years). Without an open period that covers a document's date, GL posting silently does nothing.
4. Create the customer's users and roles. Each one sees the agreement on first login, and acceptances are recorded in `useragreementacceptance` with time, IP and browser.

## 7. Updating to a new version

```bash
# 1. Snapshot first: RDS → Databases → Actions → Take snapshot  (name: pre-<date>)
cd ~/ngm/nextgenmanager && git pull
cd ~/ngm/ui && git pull
cd ~/ngm/nextgenmanager/deploy/aws
docker compose up -d --build
docker compose logs -f backend      # new Flyway migrations run on startup
```

Downtime is the backend restart, about 1–2 minutes. Do updates outside the customer's working hours. If a migration fails, the backend won't start. Restore the snapshot, check out the previous commit, and rebuild.

When the agreement text changes, edit `config/user-agreement.html`, bump `APP_AGREEMENT_VERSION` in `.env`, then run `docker compose up -d backend`. Every user is asked to accept again, and the record of their earlier acceptance stays.

## 8. Backups and monitoring

- **RDS**: automated backups (14 days, point-in-time restore) plus a manual snapshot before every update. Copy a monthly snapshot to a second region if the customer wants off-site copies.
- **S3**: versioning is on. Add a lifecycle rule that expires non-current versions after 90 days.
- **Test a restore** once before go-live: restore a snapshot to a new instance and point a copy of the stack at it. Until you've done a restore, you don't know that your backups work.
- **Monitoring**: CloudWatch alarms on EC2 `StatusCheckFailed` and CPU > 80%, and RDS `FreeStorageSpace` < 2 GB and CPU > 80%, sent to your email via SNS. Add a free uptime check (e.g. UptimeRobot) on `https://erp.customer.com/api/auth/recovery/info`.
- **Logs**: `docker compose logs backend`. They're rotated at 5 × 20 MB per container.

## Security checklist before handing over

- [ ] RDS is not publicly accessible; its security group only admits `ngm-app`
- [ ] Port 22 is closed, or open only to your IP
- [ ] `.env` has fresh secrets, `chmod 600`, and is never committed
- [ ] The seeded admin password has been changed
- [ ] Swagger is disabled (the compose file does this): `https://<domain>/swagger-ui/index.html` should not load
- [ ] `MCP_SERVER_ENABLED=false` unless the customer actually uses AI clients
- [ ] Agreement placeholders are filled in and the text has been reviewed
- [ ] Restore test done; billing alarm set
