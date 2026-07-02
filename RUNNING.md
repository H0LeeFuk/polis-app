# Running POLIS locally

Full stack: **Spring Boot backend** (Java 21) + **React/Vite frontend** + **PostgreSQL** (Docker).

- Frontend: http://localhost:5173  (Vite dev server, proxies `/api` → `http://localhost:8080`)
- Backend:  http://localhost:8080  (Spring Boot; `/api/auth/**` is public, everything else needs a JWT)
- Database: Postgres in Docker on host port **5433** → container 5432

Test user (created during first run): `smoketest` / `secret123`.

---

## Environment gotchas on this machine

These bit us once; the commands below already account for them.

1. **Docker CLI env override.** `DOCKER_HOST=tcp://localhost:2376` (with `DOCKER_TLS_VERIFY=1`)
   is set in the shell and overrides Docker Desktop's working context. Always run docker with
   `DOCKER_HOST=` cleared and `--context desktop-linux`.
2. **Port 5432 is taken.** A native Windows service `postgresql-x64-15` is bound to
   `0.0.0.0:5432`. If the container maps 5432 it gets shadowed and the backend connects to the
   wrong DB → `password authentication failed for user "polis"`. So we run the container on
   **5433** and point the backend at it via `SPRING_DATASOURCE_URL`.
3. **JDK version.** The project targets **Java 21**. `JAVA_HOME` on this box is JDK 19 (too old)
   and the only newer system JDK is 24, which breaks Lombok 1.18.34 (the version Spring Boot
   3.3.5 pins) — every generated getter/setter fails to compile. Use a **JDK 21**.
   A portable Temurin 21 lives under the Claude scratchpad; or install one and set `JAVA_HOME`.

---

## Startup (3 terminals or background processes)

Run from the `polis-app/` directory.

### 1. Database (Postgres on host port 5433)

```bash
# create once
DOCKER_HOST= DOCKER_TLS_VERIFY= docker --context desktop-linux run -d --name polis-db \
  -e POSTGRES_DB=polis -e POSTGRES_USER=polis -e POSTGRES_PASSWORD=polis \
  -p 5433:5432 postgres:16-alpine

# after the first time, just restart it
DOCKER_HOST= docker --context desktop-linux start polis-db

# check it's ready
DOCKER_HOST= docker --context desktop-linux exec polis-db pg_isready -U polis
```

> The bundled `docker-compose.yml` maps 5432 (which conflicts with the native PG-15).
> Prefer the `docker run ... -p 5433:5432` above until compose is updated.

### 2. Backend (Spring Boot, needs JDK 21 + the 5433 datasource)

```bash
cd backend
export JAVA_HOME="<path-to-jdk-21>"        # e.g. C:/Program Files/Java/jdk-21, or the portable Temurin 21
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/polis"
mvn spring-boot:run
```

Ready when the log shows `Tomcat started on port 8080` and `Started PolisApplication`.
Flyway applies the migrations automatically on startup.

### 3. Frontend (Vite dev server)

```bash
cd frontend
npm install        # first time only
npm run dev        # serves http://localhost:5173
```

---

## Smoke test (backend only, no browser)

```bash
# register -> returns a JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"smoketest","email":"s@t.com","password":"secret123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# authenticated call returns seeded world data
curl -s http://localhost:8080/api/world -H "Authorization: Bearer $TOKEN" | head -c 300
```

## Shutting down

```bash
DOCKER_HOST= docker --context desktop-linux stop polis-db   # keeps the data volume
# stop the mvn and npm processes (Ctrl-C in their terminals)
```
