# Operational reporting

At 00:05 UTC the report job recalculates the previous UTC day from owned daily metrics
and upserts one `FINALIZED` row per date. A run catches up at most seven missing days.
GET requests never generate reports. Overview, report, incident and job APIs require ADMIN.
