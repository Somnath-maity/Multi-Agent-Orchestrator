-- Workflows table: stores top-level workflow definitions and state
CREATE TABLE workflows (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    goal       TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    plan_json  TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- Steps table: individual execution steps within a workflow
CREATE TABLE steps (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID             NOT NULL REFERENCES workflows(id),
    type        VARCHAR(20)      NOT NULL,
    depends_on  TEXT             DEFAULT '',
    status      VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
    input       TEXT,
    output      TEXT,
    attempt     INT              NOT NULL DEFAULT 0,
    eval_score  DOUBLE PRECISION,
    created_at  TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP        NOT NULL DEFAULT now()
);

-- Index for efficient lookup of steps by workflow
CREATE INDEX idx_steps_workflow_id ON steps(workflow_id);
