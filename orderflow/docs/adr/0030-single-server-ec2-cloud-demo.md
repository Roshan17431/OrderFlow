# ADR 0030: Single-server EC2 portfolio deployment

Status: Accepted for prepared cloud-demo configuration; resource creation awaits authorization.

Use one Ubuntu 24.04 EC2 host running private Docker Compose services behind
Caddy. Start capacity evaluation with configurable `t3.xlarge` x86/16 GiB and
30 GiB root plus 100 GiB retained encrypted gp3 data storage. Keep all
`/opt/orderflow` state on the separate volume and use SSM rather than public SSH.

This makes an 11-service portfolio deployment understandable and reproducible
without silently provisioning managed databases, replicated brokers or load
balancers. It is a single point of failure with possible release downtime, not
enterprise HA. Capacity must be measured; T3 standard credits can throttle.
Backups and isolated recovery exercises remain mandatory. Higher reliability
requires a separately scoped architecture and cost review.
