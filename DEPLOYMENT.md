# Production Deployment Guide

This guide covers deploying **rapid-engine** to a single AWS EC2 VM, with a
**remote/managed Redis and selected messaging broker** (RabbitMQ or Kafka), and
centralized logging via **Grafana Cloud Loki**.

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
`env.docker.example` as a template and fill in the remote Redis credentials,
`MESSAGING_BROKER`, the **selected broker's** endpoint and credentials, and the
Grafana Loki values.

`MESSAGING_BROKER` is mandatory in the `prod` profile and must be exactly
`rabbitmq` or `kafka`; production does not fall back to RabbitMQ. The production
Compose file provisions neither broker: the selected broker must be externally
managed and reachable from the VM.

The CI pipeline copies `docker-compose.prod.yml` and `alloy/config.alloy` to the
VM automatically, but for a first manual run you can scp them yourself.

---

## 3. Production broker configuration

The application has one broker-neutral path:

```text
EventProducer -> EventPublisher -> RabbitEventPublisher | KafkaEventPublisher
```

Select one publisher and configure its externally managed endpoint. The
unselected broker's connection values are not used for delivery.

### RabbitMQ

```dotenv
MESSAGING_BROKER=rabbitmq
RABBITMQ_HOST=rabbitmq.example.internal
RABBITMQ_PORT=5671
RABBITMQ_USERNAME=rapid-engine
RABBITMQ_PASSWORD=<secret>
RABBITMQ_VHOST=/
RABBITMQ_SSL_ENABLED=true
RABBITMQ_MATCHES_EXCHANGE=matches.exchange
RABBITMQ_MATCHES_QUEUE=matches.queue
RABBITMQ_MATCHES_ROUTING_KEY=matches
RABBITMQ_RESULTS_EXCHANGE=results.exchange
RABBITMQ_RESULTS_QUEUE=results.queue
RABBITMQ_RESULTS_ROUTING_KEY=results
```

The application declares and binds the matches and results topic exchanges,
queues, and routing keys. Its publisher waits for broker confirms and rejects
returned (unroutable) messages before the ingestion cursor can advance.

### Kafka

```dotenv
MESSAGING_BROKER=kafka
KAFKA_BOOTSTRAP_SERVERS=kafka-1.example.internal:9093,kafka-2.example.internal:9093
KAFKA_MATCHES_TOPIC=matches
KAFKA_RESULTS_TOPIC=results
KAFKA_PARTITIONS=3
KAFKA_REPLICATION_FACTOR=3
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=SCRAM-SHA-512
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="<user>" password="<secret>";
KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM=https
# Set truststore/keystore paths and passwords only when your provider requires them.
# KAFKA_SSL_TRUSTSTORE_LOCATION=/etc/rapid-engine/kafka.truststore.p12
# KAFKA_SSL_TRUSTSTORE_PASSWORD=<secret>
# KAFKA_SSL_TRUSTSTORE_TYPE=PKCS12
```

`KAFKA_BOOTSTRAP_SERVERS` is required when Kafka is selected. The application
provisions the configured `matches` and `results` topics through Kafka Admin,
then sends snake-case JSON records with a **null key**, waiting for each send
acknowledgement.

For managed Kafka, use the provider's required `KAFKA_SECURITY_PROTOCOL`
(typically `SASL_SSL`), mechanism, and JAAS login. Hostname verification defaults
to `https`; do not disable it in production. Java's default CA trust is sufficient
for most providers. Configure the optional truststore/keystore variables only for
private CAs or mutual TLS, and mount those files into the container at the same
paths. Plaintext remains the local default and requires none of these credentials.

Kafka ACLs apply to the authenticated identity from `KAFKA_SASL_JAAS_CONFIG`
(or the certificate principal for mTLS), not the VM or container user. That
principal needs topic describe/create/alter privileges (and write permission for
the application producer) for both configured topics. If cluster policy disables
auto-provisioning or denies those privileges, create the topics
out of band with the configured partition count and replication factor before
deploying. `KAFKA_REPLICATION_FACTOR` cannot exceed the number of available,
eligible brokers; use a replication factor of `1` only where the cluster and
availability requirements permit it.

### Delivery and consumer requirement

Delivery is at least once for each fetched upstream response batch. The Redis
`delta_last_id` is written only after all events in that batch are acknowledged
by the selected broker. A failed publish leaves the prior cursor intact, so a
later retry can redeliver already acknowledged events from the same batch.
Consumers must deduplicate by a stable event identity.

---

## 4. Centralized logging (Grafana Cloud Loki)

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

## 5. Deploying

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
export APP_ENV_FILE="$HOME/rapid-engine/.env.prod"
test -f "$APP_ENV_FILE"
docker compose -f docker-compose.prod.yml --env-file "$APP_ENV_FILE" pull
docker compose -f docker-compose.prod.yml --env-file "$APP_ENV_FILE" up -d
```

---

## 6. Quick checks after deploy

```bash
docker compose -f docker-compose.prod.yml ps          # both services Up?
docker logs --tail 50 rapid-engine                    # app starting cleanly?
docker logs --tail 50 alloy                           # shipping to Loki?
```

If Redis fails to connect, double-check `REDIS_SSL_ENABLED=true` and that the
password/host are correct (see the `redis-cli --tls ping` test above).
