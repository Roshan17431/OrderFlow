# Cloud monitoring

The optional `observability` Compose profile reuses the existing eleven Spring Prometheus targets, alerts, Grafana dashboards and datasource provisioning. Cloud-specific files live in `infrastructure/cloud/monitoring/`; local monitoring files remain unchanged. Add `--profile observability` consistently to preflight, startup, verification and rollback commands when enabled.

Prometheus scrapes private application `/actuator/prometheus`, node-exporter and blackbox-exporter. Private availability probes cover frontend, gateway readiness and Caddy liveness with five-second timeouts. Host metrics cover memory and the persistent ext4 mount, plus backup textfile metrics under `/opt/orderflow/data/node-exporter/backup.prom`. New alerts cover unavailable services, probe failure, low disk/memory, elevated 5xx, a backup older than 26 hours and a restore exercise older than 31 days; missing backup metrics alert as well. Existing outbox, DLT, scheduler, payment, delivery and notification alerts remain loaded.

Grafana and Prometheus publish **only** `127.0.0.1:3001` and `127.0.0.1:9090`. Access through an authorized SSM session; security groups must not expose these ports:

```sh
aws ssm start-session --target <approved-instance-id> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

Open `http://localhost:3001`, using the protected configured Grafana administrator. Use an equivalent session for Prometheus port 9090 when needed. Disable anonymous access and signup; Grafana credentials do not use local demo defaults. No exporter, admin API or actuator path is public.

Prometheus retains 15 days or 2 GB, whichever limit is reached first. Grafana state is persisted on EBS. All Compose services rotate stdout JSON logs at three 10 MB files; Caddy separately rotates three 10 MiB redacted access logs. Never run resolved Compose config with real credentials into logs. JVM heap uses container percentages, and all services have memory bounds. These are starting limits requiring measurement, not evidence of a capacity benchmark.

Alerts in Prometheus are evaluable rules; **notification delivery is not configured** by merely adding the rules. Configure an approved private Alertmanager/contact receiver and test delivery before claiming an on-call alert system. CloudWatch alarms defined by Terraform cover the host where enabled; they require an approved destination and a tested notification path. A same-host Prometheus cannot notify after complete EC2 failure, so external availability checks and AWS status alarms remain necessary. The supplied public HTTPS blackbox module is available for an approved external probe; the default private scrape targets do not test DNS, ACME, public network ingress or certificate expiry.

Backup tooling writes `orderflow_backup_last_success_timestamp_seconds` and `orderflow_backup_restore_verified_timestamp_seconds`. Treat a restore-verification timestamp as evidence of an isolated test only, never a production restore. A first deployment should alert until backup and restore procedures have actually run. Do not fabricate timestamps to silence alerts.

Check metrics/rules with the same versioned Prometheus image used by Compose:

```sh
promtool check config infrastructure/cloud/monitoring/prometheus.yml
promtool check rules monitoring/prometheus/rules/orderflow-alerts.yml \
  infrastructure/cloud/monitoring/cloud-alerts.yml
```

Configuration references container `/etc/prometheus` paths, so run configuration validation with the cloud mounts in an isolated container (without starting a Prometheus server), or stage the same layout. Verify all eleven targets and three probes, host memory/disk, dashboard queries and actual alert transitions after deployment. Local configuration validation does not establish that live targets are healthy.
