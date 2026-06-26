# Production Deployment Guide

This guide covers deploying **rapid-engine** to a single AWS EC2 VM, with a
**remote/managed Redis** (and RabbitMQ), and centralized logging via **Grafana
Cloud Loki**.

---

## 1. How remote Redis works (your main question)

Your local setup uses a bare `localhost:6379` with **no username and no
password**. In production you'll point the app at a managed Redis (e.g.
[Upstash](https://upstash.com/), AWS ElastiCache, Redis Cloud), which **does**
require credentials and TLS.

**Good news: you don't change any code.** The app reads all Redis settings from
environment variables (see `src/main/resources/application-prod.yaml`):

```yaml
spring:
  data:
    redis:
      username: ${REDIS_USERNAME:default}   # Redis 6+ ACL user; Upstash uses "default"
      host:     ${REDIS_HOST}
      port:     ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      database: ${REDIS_DATABASE:0}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:true}   # managed Redis = TLS on
      timeout:  ${REDIS_TIMEOUT:10000}
```

Spring Boot auto-configures the `RedisConnectionFactory` from these properties,
so `RedisConfig.java` needs zero changes between local and prod.

### Local (dev) vs Remote (prod)

| Setting              | Local (`.env.docker`) | Remote / Upstash (`.env.prod`)              |
|----------------------|-----------------------|---------------------------------------------|
| `REDIS_HOST`         | `redis` / `localhost` | `xxxx.upstash.io`                           |
| `REDIS_PORT`         | `6379`                | `6379` (Upstash) or provider's TLS port     |
| `REDIS_USERNAME`     | `default` (unused)    | `default`                                   |
| `REDIS_PASSWORD`     | *(empty)*             | `<your-upstash-password>`                   |
| `REDIS_SSL_ENABLED`  | `false`               | `true`                                      |

### Where to get the values (Upstash example)

1. Create a database at https://console.upstash.com/.
2. Open the database → **Details** tab.
3. Copy the **Endpoint** (host), **Port**, and **Password**.
   - Username is `default`.
   - Keep **TLS/SSL enabled** → `REDIS_SSL_ENABLED=true`.

So your prod `.env.prod` Redis block looks like:

```dotenv
REDIS_HOST=elated-cat-12345.upstash.io
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=AbCdEf...your-secret...
REDIS_SSL_ENABLED=true
REDIS_TIMEOUT=10000
```

### Verify the connection before deploying

```bash
# Most managed providers require TLS, so use --tls:
redis-cli -h elated-cat-12345.upstash.io -p 6379 \
  --user default -a "$REDIS_PASSWORD" --tls ping
# Expect: PONG
```

---

## 2. One-time VM setup

On the EC2 box (Ubuntu assumed):

```bash
# Install Docker + compose plugin
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"   # re-login after this

# Create the app directory
mkdir -p ~/rapid-engine/alloy
cd ~/rapid-engine
```

Create `~/rapid-engine/.env.prod` (NEVER commit this — it's gitignored). Use
`env.docker.example` as a template and fill in the **remote** Redis/RabbitMQ
credentials plus the Grafana Loki values.

The CI pipeline copies `docker-compose.prod.yml` and `alloy/config.alloy` to the
VM automatically, but for a first manual run you can scp them yourself.

---

## 3. Centralized logging (Grafana Cloud Loki)

We no longer write log files to disk. The app logs **JSON to stdout**, Docker
captures it, and a **Grafana Alloy** sidecar ships it to Grafana Cloud Loki.

1. Create a free account at https://grafana.com/ → **Grafana Cloud**.
2. Go to **Connections → Add new connection → Loki / "Send logs"**.
3. Copy the **URL**, **User/Username** (a numeric ID), and generate an
   **API token** (password). Put them in `.env.prod`:

```dotenv
GRAFANA_LOKI_URL=https://logs-prod-XXX.grafana.net/loki/api/v1/push
GRAFANA_LOKI_USER=123456
GRAFANA_LOKI_API_KEY=glc_xxxxxxxxxxxxxxxxxxxxxxxx
```

4. View logs in Grafana → **Explore** → select the Loki datasource →
   query `{job="rapid-engine"}`. You can filter by level, e.g.
   `{job="rapid-engine", level="ERROR"}`.

---

## 4. Deploying

### Automatic (recommended)

Pushing to `main` runs the GitHub Actions pipeline:

`test → build → docker-build → docker-push → deploy`

The **deploy** job SSHes to the VM, copies the compose + Alloy config, pulls the
new image from Docker Hub, and restarts the stack.

Required GitHub repository **secrets**:

| Secret               | Purpose                              |
|----------------------|--------------------------------------|
| `DOCKERHUB_USERNAME` | Push the image to Docker Hub         |
| `DOCKERHUB_TOKEN`    | Docker Hub access token              |
| `EC2_HOST`           | VM public IP / DNS                   |
| `EC2_USER`           | SSH user (e.g. `ubuntu`)             |
| `EC2_SSH_KEY`        | Private SSH key for the VM           |

### Manual

```bash
cd ~/rapid-engine
export IMAGE=bixoloo/rapid-engine:latest
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

---

## 5. Quick checks after deploy

```bash
docker compose -f docker-compose.prod.yml ps          # both services Up?
docker logs --tail 50 rapid-engine                    # app starting cleanly?
docker logs --tail 50 alloy                           # shipping to Loki?
```

If Redis fails to connect, double-check `REDIS_SSL_ENABLED=true` and that the
password/host are correct (see the `redis-cli --tls ping` test above).
