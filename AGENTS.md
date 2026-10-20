# Repository Development Rules

## Stack and structure

- Backend: Java 21, Spring Boot 3.x, Maven, PostgreSQL, Flyway, REST.
- Frontend: React, TypeScript, Vite, Tailwind CSS, Axios.
- Keep backend dependencies flowing from controller to service to provider/client or repository.
- Use DTOs at API boundaries. Keep application domain models independent from external provider models.
- Use constructor injection and focused classes. Apply SOLID principles where they simplify the design; avoid unnecessary abstractions.

## Provider integrations

- Keep Skin Analysis and Skin Simulation behind their provider interfaces and dedicated adapters.
- Do not invent external API request or response contracts. Verify changes against current official provider documentation.
- Use `YOUCAM_MOCK=true` for deterministic local development and tests. Do not make real provider calls unless the task explicitly requires and authorizes them.
- Keep provider credentials on the backend. Supply secrets through environment variables or an approved secret store; never add credentials as code or configuration defaults.
- Never log credentials, authorization headers, signed upload URLs, image bytes, or sensitive image data. Keep provider diagnostics bounded and sanitized.

## Security and data handling

- Validate external input, including uploaded file type and size.
- Do not expose provider credentials, signed URLs, internal provider references, or sensitive image data through frontend code or public API responses.
- Keep local credentials and private test images out of Git.
- Avoid medical-diagnosis, severity, confidence, or clinical-outcome claims unless supported by the actual API contract and product requirements.

## Change workflow

Before modifying code:

1. Inspect these rules, the relevant documentation, and the existing implementation.
2. Identify affected files and preserve unrelated working behavior.
3. Make the smallest complete change; do not rewrite working code unnecessarily.
4. Add or update relevant unit or integration tests when behavior changes.
5. Run the relevant tests and builds, then address failures.
6. Summarize the changes and verification results.

Backend verification:

```sh
cd backend
mvn test
mvn package
```

Frontend verification:

```sh
cd frontend
npm test
npm run build
```

Keep REST contracts stable unless a confirmed bug or an explicit requirement calls for a change. Prefer readable, meaningful names and comments that explain non-obvious decisions.
