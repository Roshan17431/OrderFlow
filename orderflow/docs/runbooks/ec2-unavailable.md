# EC2 unavailable

1. Record time, public symptoms, current release manifest, last successful backup and last verified restore. Check AWS account/region before using read-only console/CLI. A failed public curl alone does not distinguish host, DNS, security group or certificate failure.
2. Inspect EC2 status checks, instance state, approved security group 80/443, public IPv4/DNS and EBS attachment. Check CloudWatch if configured. Same-host Prometheus may be unavailable too.
3. Attempt authorized SSM access. Check `systemctl status docker`, `df -h /opt/orderflow`, `free -m`, kernel OOM messages, mount source/UUID and container health. Avoid commands that print environment secrets. If SSM cannot connect, inspect IAM/agent/network/time before choosing a recovery path.
4. Prefer repair/restart of the existing host after assessing impact. A host reboot/replacement requires explicit authorization where applicable. Never detach a mounted database volume from a running host or format storage to make bootstrap pass.
5. For approved replacement, snapshot and preserve the existing encrypted EBS first. Attach the correct volume, rerun guarded bootstrap with its exact volume ID and **no blank-format authorization**, restore SSM-scoped secrets if required, and start the last healthy manifest. Do not restore older business data automatically.
6. If storage is unusable, follow the backup restore procedure with a verified encrypted backup and a separate isolated rehearsal. Document measured RPO/RTO, missing data and explicit restore approval.
7. Verify ownership/Flyway, stable JWT key ID, Redis-backed revocation, Kafka cluster ID/offsets, private readiness, public HTTPS and core role flows before declaring recovery. Report unresolved loss honestly; do not imply HA.
