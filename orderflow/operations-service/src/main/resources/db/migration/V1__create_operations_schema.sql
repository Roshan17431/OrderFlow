CREATE TABLE daily_operational_metric (
 id UUID PRIMARY KEY, metric_date DATE NOT NULL, metric_name VARCHAR(80) NOT NULL,
 dimension VARCHAR(120) NOT NULL DEFAULT 'all', count_value BIGINT NOT NULL DEFAULT 0,
 decimal_value NUMERIC(19,2), updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_daily_metric UNIQUE(metric_date,metric_name,dimension),
 CONSTRAINT ck_daily_metric_count CHECK(count_value >= 0));
CREATE INDEX idx_daily_metric_date ON daily_operational_metric(metric_date,metric_name);

CREATE TABLE operational_incident (
 id UUID PRIMARY KEY, incident_type VARCHAR(80) NOT NULL, severity VARCHAR(16) NOT NULL,
 source_service VARCHAR(64) NOT NULL, aggregate_type VARCHAR(64) NOT NULL,
 aggregate_id UUID, reason_code VARCHAR(80), correlation_id VARCHAR(128),
 occurred_at TIMESTAMPTZ NOT NULL, resolved BOOLEAN NOT NULL DEFAULT FALSE,
 resolved_at TIMESTAMPTZ, resolution_note VARCHAR(300), created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_incident_severity CHECK(severity IN('INFO','WARNING','CRITICAL')));
CREATE INDEX idx_incident_query ON operational_incident(resolved,severity,occurred_at DESC);

CREATE TABLE processed_event (
 event_id UUID NOT NULL, consumer_name VARCHAR(120) NOT NULL, event_type VARCHAR(80) NOT NULL,
 aggregate_id UUID NOT NULL, processed_at TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(128),
 PRIMARY KEY(event_id,consumer_name));
CREATE INDEX idx_operations_processed_at ON processed_event(processed_at);

CREATE TABLE scheduled_job_lock (
 job_name VARCHAR(120) PRIMARY KEY, locked_until TIMESTAMPTZ NOT NULL,
 locked_at TIMESTAMPTZ, locked_by VARCHAR(128), last_started_at TIMESTAMPTZ,
 last_completed_at TIMESTAMPTZ, last_status VARCHAR(24), last_duration_ms BIGINT,
 failure_count BIGINT NOT NULL DEFAULT 0, last_error_code VARCHAR(80), updated_at TIMESTAMPTZ NOT NULL);

CREATE TABLE scheduled_job_run (
 id UUID PRIMARY KEY, job_name VARCHAR(120) NOT NULL, instance_id VARCHAR(128) NOT NULL,
 started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ, status VARCHAR(24) NOT NULL,
 processed_count BIGINT NOT NULL DEFAULT 0, failure_count BIGINT NOT NULL DEFAULT 0,
 duration_ms BIGINT, error_code VARCHAR(80), correlation_id VARCHAR(128),
 CONSTRAINT ck_job_run_status CHECK(status IN('RUNNING','SUCCEEDED','PARTIAL','FAILED','SKIPPED_LOCKED')));
CREATE INDEX idx_job_run_history ON scheduled_job_run(job_name,started_at DESC);

CREATE TABLE daily_report (
 id UUID PRIMARY KEY, report_date DATE NOT NULL UNIQUE, status VARCHAR(16) NOT NULL,
 generated_at TIMESTAMPTZ NOT NULL, finalized_at TIMESTAMPTZ,
 orders_created BIGINT NOT NULL DEFAULT 0, payments_succeeded BIGINT NOT NULL DEFAULT 0,
 payments_failed BIGINT NOT NULL DEFAULT 0, payment_volume NUMERIC(19,2) NOT NULL DEFAULT 0,
 refund_volume NUMERIC(19,2) NOT NULL DEFAULT 0, inventory_reservation_failures BIGINT NOT NULL DEFAULT 0,
 deliveries_completed BIGINT NOT NULL DEFAULT 0, deliveries_failed BIGINT NOT NULL DEFAULT 0,
 notifications_delivered BIGINT NOT NULL DEFAULT 0, notifications_failed BIGINT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, CONSTRAINT ck_report_status CHECK(status IN('DRAFT','FINALIZED')));
CREATE INDEX idx_daily_report_date ON daily_report(report_date DESC);
