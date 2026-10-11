CREATE TABLE scheduled_job_lock (job_name VARCHAR(120) PRIMARY KEY,locked_until TIMESTAMPTZ NOT NULL,locked_at TIMESTAMPTZ,locked_by VARCHAR(128),last_started_at TIMESTAMPTZ,last_completed_at TIMESTAMPTZ,last_status VARCHAR(24),last_duration_ms BIGINT,failure_count BIGINT NOT NULL DEFAULT 0,last_error_code VARCHAR(80),updated_at TIMESTAMPTZ NOT NULL);
CREATE TABLE scheduled_job_run (id UUID PRIMARY KEY,job_name VARCHAR(120) NOT NULL,instance_id VARCHAR(128) NOT NULL,started_at TIMESTAMPTZ NOT NULL,completed_at TIMESTAMPTZ,status VARCHAR(24) NOT NULL,processed_count BIGINT NOT NULL DEFAULT 0,failure_count BIGINT NOT NULL DEFAULT 0,duration_ms BIGINT,error_code VARCHAR(80),correlation_id VARCHAR(128),CONSTRAINT ck_order_job_status CHECK(status IN('RUNNING','SUCCEEDED','PARTIAL','FAILED','SKIPPED_LOCKED')));
CREATE INDEX idx_order_job_run ON scheduled_job_run(job_name,started_at DESC);
CREATE INDEX idx_order_processed_retention ON processed_event(processed_at);
CREATE INDEX idx_order_outbox_retention ON outbox_event(published_at) WHERE status='PUBLISHED';
CREATE INDEX idx_order_idempotency_retention ON idempotency_record(completed_at) WHERE completed_at IS NOT NULL;
