# Split-phase shipment label creation

Shipment creation is a local transactional operation. `ShippingService.create` validates the confirmed order, persists the shipment in `PENDING`, and appends `ShipmentCreated`; it does not call a carrier while holding a database transaction.

`ShipmentLabelCreationWorker` claims pending shipments with PostgreSQL `FOR UPDATE SKIP LOCKED` and a 120-second lease. It releases the claim transaction before calling `CarrierGateway`, then records the provider reference and `ShipmentLabelCreated` event in a short transaction. Failed calls are retried with bounded exponential backoff, always reusing the same shipment ID as the provider idempotency key.

The deterministic sandbox carrier returns `sandbox-shipment-{shipmentId}`, so retries are stable and require no external credentials. A production carrier adapter must guarantee idempotent create by shipment ID or provide a provider lookup/reconciliation API; a timeout must never cause a new shipment ID to be generated. This sandbox path is not a live carrier integration.
