# SkinCart AI Development Rules

## Project scope

SkinCart AI is an AI-powered skincare shopping experience. The planned user journey is:

1. Upload a selfie.
2. Analyze skin with YouCam.
3. Identify skin concerns.
4. Build a personalized skincare routine.
5. Recommend products.
6. Simulate skincare results with YouCam.
7. Show a before-and-after view and provide a path to shop.

Do not implement application functionality until the relevant implementation phase is authorized and its requirements are available. In particular, do not guess YouCam API request or response formats. The official API documentation must be reviewed before implementing the YouCam integration.

## Architecture and stack

- Backend: Java 21, Spring Boot 3.x, Maven, PostgreSQL, REST APIs.
- Frontend: React, TypeScript, Vite, Tailwind CSS, Axios.
- Backend dependency direction: Controller → Service → Client and/or Repository.
- Use DTOs at API and layer boundaries. Keep internal domain models independent of provider models.
- Isolate YouCam behind a dedicated client/adapter. The frontend must never receive or contain the YouCam API key.
- Use constructor dependency injection and small, focused classes. Apply SOLID principles where they simplify the design; avoid unnecessary abstractions and over-engineering.

## Security, privacy, and configuration

- Load configuration and credentials from environment variables or an approved secret store. Do not hardcode credentials.
- Never log API keys, uploaded image contents, or other sensitive image data. Keep logs useful and avoid personal data.
- Validate external input and apply appropriate request-size and file-type limits when upload functionality is implemented.
- Decide image storage, consent, retention, and deletion behavior before implementing persistence for selfies or simulations.
- Keep local secrets out of Git. `.env.example` may document variable names and safe placeholders only.
- Support `YOUCAM_MOCK=true` for deterministic development and test responses without calling YouCam or consuming API credits.

## Quality and change workflow

Before changing application code:

1. Inspect the repository and applicable instructions.
2. Understand the existing implementation and identify affected files.
3. Make the smallest complete change that fulfills the task.
4. Add or update unit tests for business logic and integration tests for important APIs when appropriate.
5. Run the relevant tests and build, then fix failures.
6. Summarize the changes and verification performed.

Do not rewrite working code unnecessarily. Keep API contracts explicit, handle errors consistently, and use meaningful logging without secrets or sensitive image data.

## Current phase

Phase 0 is repository planning and documentation only. Do not add backend or frontend business functionality until a later phase is requested.
