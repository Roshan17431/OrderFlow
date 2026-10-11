# Delivery assignment backlog

## Symptoms

Paid orders remain awaiting delivery, unassigned delivery count/age grows, agents appear stuck unavailable, or assignment scheduler failures rise.

## Investigation

Check eligible agent availability, delivery status, scheduler lease and last run, database contention, Kafka lag/outbox, and correlation IDs. Confirm only one scheduler instance owns the active lease.

## Recovery and verification

Restore agents/dependencies and allow bounded cluster-safe assignment to resume. Verify one delivery and agent per order, one assignment event, agent availability changes atomically, and no delivered/failed state regresses. Escalate double assignment, payment-state mismatch, or a lease that cannot expire safely.
