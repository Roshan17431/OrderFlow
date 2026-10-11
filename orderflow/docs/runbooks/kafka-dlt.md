# Kafka DLT

## Symptoms

DLT record/offset counts increase, consumer retry exhaustion logs appear, or workflow states stop at an asynchronous boundary.

## Investigation

Record topic, partition, offset, event type/version, aggregate ID, and correlation ID without copying sensitive payloads. Check consumer deployment/version, schema compatibility, database constraints, and dependency health. Keep the DLT immutable while classifying permanent versus transient failures.

## Recovery and verification

Fix the consumer/configuration first. Replay one approved record with its original event ID and headers, verify one state transition and one processed-event row, then replay bounded batches. Confirm DLT stops growing, normal lag drains, operations incidents resolve, and no aggregate regresses. Escalate unknown versions or missing idempotency history; never reset an entire group casually.
