# SkinCart AI

SkinCart AI is a hackathon demo for a skincare discovery experience. It connects selfie-based skin analysis with a deterministic demo product catalog, an AM/PM routine, and a visual skin simulation.

## What the application does

A user uploads a JPEG or PNG selfie. The backend validates the image and sends it through the configured Skin Analysis provider. The application displays returned skin concerns and scores, selects matching products from its demo catalog, builds a daily routine, and can request a visual simulation for a supported concern.

### Main user flow

**Upload selfie -> YouCam Skin Analysis -> skin concerns -> deterministic product recommendations -> AM/PM skincare routine -> YouCam visual skin simulation -> before/after visualization**

## Key features

- JPEG/PNG upload with backend validation.
- Skin concern results and scores from the selected provider.
- Deterministic product ranking based on analyzed concerns, catalog data, and ingredient matches.
- AM and PM routines assembled from the same demo catalog.
- Visual simulation with an original/preview comparison.
- A deterministic mock provider for analysis and simulation.

## Technology stack

- **Backend:** Java 21, Spring Boot 3.x, Maven, PostgreSQL, Flyway
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, Axios
- **External provider:** YouCam, isolated behind backend provider adapters

## Architecture overview

The browser calls Spring REST controllers. Controllers delegate to services, which coordinate provider interfaces and repositories. Provider-specific HTTP and DTO handling lives in the YouCam adapter package; application services use SkinCart models. PostgreSQL stores application analysis data and the seeded demo catalog.

See [docs/architecture.md](docs/architecture.md) for the component and request flows.

## Local setup

### Requirements

Install Java 21, Maven, Node.js/npm, and PostgreSQL.

### PostgreSQL setup

Create a local database named `skincart` (for example, with `createdb skincart`). Configure `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` for your PostgreSQL instance. Flyway applies the schema and demo catalog migrations when the backend starts.

### Backend setup

From the repository root:

```sh
cd backend
export YOUCAM_MOCK=true
mvn spring-boot:run
```

Set the database variables above in the same shell if your local PostgreSQL connection needs them. The backend listens on port 8080 by default.

### Frontend setup

In another terminal:

```sh
cd frontend
npm ci
export VITE_API_BASE_URL=http://localhost:8080
npm run dev
```

Open the Vite address shown in the terminal (normally `http://localhost:5173`). Set `VITE_API_BASE_URL` to the backend origin without an `/api` suffix; the frontend adds the API paths.

## Environment variables

| Variable | Used by | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | Backend | HTTP port; defaults to 8080. |
| `DATABASE_URL` | Backend | PostgreSQL JDBC connection URL. |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Backend | PostgreSQL credentials. |
| `CORS_ALLOWED_ORIGIN` | Backend | Allowed browser origin; defaults to the local Vite origin. |
| `YOUCAM_MOCK` | Backend | Selects deterministic mock providers when `true`; set explicitly for local development. |
| `YOUCAM_API_KEY` | Backend only | Supplies YouCam authentication for real-provider mode. Never put this in frontend configuration. |
| `YOUCAM_BASE_URL` | Backend | Optional YouCam API base URL override. |
| `YOUCAM_POLL_INTERVAL` | Backend | Provider task polling interval; defaults to 10 seconds. |
| `YOUCAM_MAX_POLL_ATTEMPTS` | Backend | Maximum provider polling attempts; capped at six. |
| `YOUCAM_CONNECT_TIMEOUT`, `YOUCAM_REQUEST_TIMEOUT` | Backend | Provider connection and request timeouts. |
| `YOUCAM_CONCERNS` | Backend | Skin concerns requested from the analysis provider. |
| `MAX_UPLOAD_SIZE`, `MAX_UPLOAD_REQUEST_SIZE` | Backend | Multipart upload limits. |
| `VITE_API_BASE_URL` | Frontend | Backend origin; defaults to `http://localhost:8080`. |

Provide credentials through the backend process environment or an approved secret store. Do not commit credential-bearing environment files.

## Mock mode

Set `YOUCAM_MOCK=true` in the backend process for development and tests. The mock analysis and simulation providers return deterministic data and do not call YouCam. Set this explicitly before starting the backend.

## Real YouCam API mode

For a real provider run, set `YOUCAM_MOCK=false` and provide `YOUCAM_API_KEY` to the backend process. Keep provider credentials and provider-specific requests on the server. Do not put credentials in Vite variables or browser code. Real mode sends the uploaded selfie to YouCam for processing.

## API endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/skin-analysis` | Upload multipart form data with an `image` field. |
| `GET` | `/api/v1/skin-analysis/{analysisId}` | Read analysis status and results. |
| `GET` | `/api/v1/skin-analysis/{analysisId}/recommendations` | Read ranked demo-catalog products. |
| `GET` | `/api/v1/skin-analysis/{analysisId}/routine` | Read the AM/PM routine. |
| `POST` | `/api/v1/skin-analysis/{analysisId}/simulation` | Request a simulation with a `concernCode` JSON field. |

The YouCam task runs asynchronously at the provider. The backend service handling the upload submits and polls that task before returning; the frontend then reads the persisted analysis resource and retries status checks as needed. Provider polling defaults to 10 seconds and at most six attempts.

## Testing

Run backend tests and package the application:

```sh
cd backend
mvn test
mvn package
```

Run frontend tests and create a production build:

```sh
cd frontend
npm test
npm run build
```

These commands use the project's test configuration and do not require a real YouCam request.

## Limitations and demo notes

- Product recommendations come from a deterministic demo catalog. They are not retailer listings, inventory, or product availability data.
- Skin analysis is informational and is not a medical diagnosis or treatment recommendation.
- Visual simulation is a preview, not a promise of clinical or real-world results.
- The demo does not include checkout or payment processing.
- In real mode, a selfie is sent to YouCam. Use mock mode for local demonstrations that should make no external provider calls.
