# Implementation Plan

## Project outcome

Deliver a skincare shopping experience in which a user can submit a selfie, receive a skin analysis, explore a personalized routine and product recommendations, preview a YouCam skin simulation, compare before and after, and shop.

The initial stack is Java 21, Spring Boot 3.x, Maven, PostgreSQL, REST, React, TypeScript, Vite, Tailwind CSS, Axios, and YouCam. The provider integration remains on hold until its actual documentation is available.

## Phases

### Phase 0 — Repository baseline and decisions

**Status: complete.** Establish the repository structure, development rules, environment-variable example, README, and initial architecture and implementation plan. No application functionality is included.

### Phase 1 — Application foundation

- Create the Spring Boot and Vite application shells using the agreed versions.
- Establish local configuration conventions and a PostgreSQL development setup.
- Set up formatting, validation, error handling, logging, and basic build/test commands.
- Keep the frontend configuration limited to the backend API URL; keep provider credentials on the backend.

**Exit criteria:** Both applications build locally; configuration has no committed secrets; the initial developer workflow is documented.

### Phase 2 — Internal contracts and deterministic mock flow

- Agree on internal DTOs and domain concepts for analysis, concerns, routines, recommendations, and simulations.
- Implement service boundaries and a deterministic provider substitute selected by `YOUCAM_MOCK=true`.
- Validate API inputs and define a consistent error response approach.
- Add focused unit tests and integration tests for the implemented APIs.

**Exit criteria:** The end-to-end UI can exercise a predictable analysis-to-recommendation flow without calling YouCam.

### Phase 3 — YouCam skin-analysis integration

- Review the supplied official YouCam documentation and confirm credentials, supported operations, constraints, and error behavior.
- Implement the provider client/adapter using only documented request and response structures.
- Map provider data into internal domain models and keep provider models inside the adapter boundary.
- Verify the integration with mocked HTTP responses and, when credentials are available, a controlled real API call.

**Exit criteria:** The analysis flow works in mock mode and can use the documented YouCam operation through the backend.

### Phase 4 — Routine and product recommendations

- Define how identified concerns map to routine steps and catalog products.
- Implement the catalog data source and recommendation rules after product data and business rules are agreed.
- Present recommendations with clear links between concerns, routine steps, and products.

**Exit criteria:** Analysis results produce a useful, explainable routine and product list.

### Phase 5 — Skin simulation and shopping journey

- Review the official YouCam simulation documentation before implementing its adapter operation.
- Connect the selected routine or products to a simulation using documented inputs.
- Build the before-and-after experience and the path from recommendations to shopping.

**Exit criteria:** The user can view the simulation result and continue to shop from the recommendation flow.

### Phase 6 — Demo readiness and hardening

- Complete end-to-end checks across mock and configured provider modes.
- Review validation, failure states, privacy, image retention/deletion, and secret handling.
- Finalize local run instructions, demo data, screenshots, and presentation materials.
- Fix issues found in the final build and integration checks.

**Exit criteria:** A repeatable hackathon demo works without requiring YouCam credits by default, and the real-provider path is clearly configurable.

## Dependencies and open decisions

- Official YouCam API documentation and credentials are required before Phase 3 or the provider portion of Phase 5.
- Product catalog source, routine/recommendation rules, user identity, and commerce/checkout scope are not yet specified.
- Selfie and simulation image storage, consent, retention, and deletion policies must be decided before persisting those assets.
- Deployment target and operational requirements are not yet specified.
