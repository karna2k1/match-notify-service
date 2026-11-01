# match-notify-service

JobRadar matching microservice (Spring Boot, port 8082). Consumes `job.ingested` in batches and scores every job for every user in two stages:

1. **Keyword score** (free): skills overlap, title vs target roles, location / work mode, experience and seniority penalties, and a feedback loop (companies you keep ignoring).
2. For candidates above the prefilter: **embedding similarity** (Gemini embeddings stored in **pgvector**) and an **LLM verdict** (Gemini or Groq behind `AiClient`) for each user's top N, cached in Redis. Everything still works with AI disabled.

Strong matches publish `job.matched` (core-api adds a Suggested card) and send an **instant email** unless it's quiet hours; the rest go into the **daily digest** (Thymeleaf). Email Save/Ignore links carry signed tokens.

API: `GET /matches`, `PATCH /matches/{id}`, `GET /feedback?token=`, `GET /stats/matches`.

Config (env): `AI_PROVIDER` (none | gemini | groq), `GEMINI_API_KEY` / `GROQ_API_KEY`, `MAIL_*`, `CORE_API_URL`; `JWT_SECRET` in `.env`.

Run: `mvn spring-boot:run`. Architecture: see jobradar-core-api.
