# SkinCart AI

SkinCart AI is a planned AI-powered skincare shopping experience. The goal is to guide a user from a selfie-based skin analysis to personalized routine and product recommendations, a skin simulation, a before-and-after view, and shopping.

## Project status

**Phase 0 — repository baseline and planning.** This repository currently contains project guidance and planning documents only. Backend and frontend application functionality has not been implemented.

## Planned technology

- **Backend:** Java 21, Spring Boot 3.x, Maven, PostgreSQL, REST APIs
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, Axios
- **External service:** YouCam API, isolated behind a backend client/adapter

The YouCam API key must remain on the backend. YouCam request and response formats will be implemented only after the official API documentation is provided. Development and tests are planned to support `YOUCAM_MOCK=true`, which returns deterministic data without calling YouCam.

## Repository layout

```text
AGENTS.md                 Development rules
backend/                  Planned Spring Boot application
frontend/                 Planned React application
docs/                     Architecture and implementation planning
docker/                   Planned local/deployment container assets
screenshots/              Planned demo screenshots
demo/                     Planned demo assets and instructions
.env.example              Safe configuration-variable examples
.gitignore
README.md
```

## Getting started

No application build or run commands are available yet because the backend and frontend have not been scaffolded. When implementation starts, copy `.env.example` to a local `.env` file and replace placeholders for the local environment. Do not commit `.env` or put the YouCam API key in frontend configuration.

## Project documents

- [Development rules](AGENTS.md)
- [Implementation plan](docs/implementation-plan.md)
- [Technical architecture](docs/architecture.md)

## Next step

Begin Phase 1 by creating the backend and frontend application shells, PostgreSQL development configuration, and documented local build workflow. Keep provider integration in mock mode until the official YouCam documentation is available.
