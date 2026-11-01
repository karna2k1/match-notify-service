CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE matches (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT        NOT NULL,
  job_id          BIGINT        NOT NULL,
  source          VARCHAR(40),
  company         VARCHAR(200)  NOT NULL,
  title           VARCHAR(300)  NOT NULL,
  location        VARCHAR(300),
  remote          BOOLEAN       NOT NULL DEFAULT false,
  url             VARCHAR(1000) NOT NULL,
  keyword_score   INT           NOT NULL,
  embedding_score INT,
  llm_score       INT,
  final_score     INT           NOT NULL,
  reasons         JSONB         NOT NULL DEFAULT '[]',
  matched_skills  JSONB         NOT NULL DEFAULT '[]',
  skill_gaps      JSONB         NOT NULL DEFAULT '[]',
  status          VARCHAR(10)   NOT NULL DEFAULT 'NEW',
  instant_sent    BOOLEAN       NOT NULL DEFAULT false,
  digest_sent     BOOLEAN       NOT NULL DEFAULT false,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT uq_matches_user_job UNIQUE (user_id, job_id)
);

CREATE INDEX idx_matches_user_score ON matches(user_id, final_score DESC);
CREATE INDEX idx_matches_pending_digest ON matches(user_id, created_at) WHERE digest_sent = false AND instant_sent = false;

-- pgvector: 768-dim embeddings (Gemini gemini-embedding-001 with outputDimensionality=768)
CREATE TABLE job_embeddings (
  job_id     BIGINT PRIMARY KEY,
  embedding  vector(768) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE profile_embeddings (
  user_id      BIGINT PRIMARY KEY,
  profile_hash VARCHAR(64) NOT NULL,
  embedding    vector(768) NOT NULL,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
