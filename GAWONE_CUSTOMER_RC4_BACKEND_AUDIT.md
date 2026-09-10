# GAWONE Customer RC4 — Backend Audit

Status: production-prep, controlled launch.

## Runtime

- App code: `GAWONE_CUSTOMER`
- Recommended versionCode: `13`
- Minimum supported versionCode: `10`
- Backend contract: `CUSTOMER-1.1`
- Maintenance mode: OFF
- Offline degraded mode: ON

## Global feature flags

Enabled: OTP_AUTH, SERVICE_CATALOG, ORDER_DRAFT, ESTIMATE, REALTIME, CHAT, RATING, SUPPORT.

Disabled/global gate: BOOKING, MATCHING, PAYMENT, PUSH, MAPS.

## Server-side pilot gate

Migration `gawone_customer_server_side_pilot_gates` adds a private per-user feature override table and server-side enforcement. No pilot user is allowlisted by default.

Migration `gawone_customer_private_feature_gate_helper` moves the feature helper to the private schema so it is not exposed as a public RPC.

`get_customer_runtime_config(versionCode)` returns global flags for anonymous users and effective user-specific flags for signed-in pilot users.

Customer booking is additionally guarded server-side when order status transitions to BOOKED. Customer matching is guarded at `start_matching` before the internal matching engine is invoked. Internal staff with `order.assign` permission retain operational access.

## Golden E2E pilot service: CLEANING

- Service display: Clean
- Booking enabled: OFF
- Matching enabled: OFF
- Area: `ID-JB-CJR-PILOT` / Cianjur Pilot — available
- Pricing: PER_HOUR
- Per hour: Rp35.000
- Minimum hours: 2
- Minimum amount: Rp70.000
- Customer platform fee: Rp2.000
- Partner commission rate: 8%
- Matching radii: 5 km → 10 km → 20 km
- Batch size: 5
- Offer TTL: 60 seconds
- Matching rule: active

## Current hard blocker

There are currently no production partner records, partner-service records, active partner presence sessions, orders, assignments, matching sessions, or payment settlements. Therefore BOOKING and MATCHING must remain closed globally until at least one verified pilot Mitra is onboarded and ready for the CLEANING service.

## Activation rule

Do not globally enable BOOKING/MATCHING merely to test UI. The next real transaction gate is:

1. Create/login a real Mitra account through OTP.
2. Complete and verify Mitra profile/KYC/service eligibility.
3. Attach CLEANING service + Cianjur Pilot area.
4. Bring Mitra online with fresh location presence.
5. Allowlist only the internal Customer pilot for BOOKING/MATCHING.
6. Enable CLEANING service booking/matching operational flags.
7. Run one Golden E2E transaction Customer → Matching → Mitra → Assignment → Execution → Chat → Completion → Rating.
8. Keep PAYMENT/PUSH/MAPS gated until real providers are configured.

No provider or transaction state should be simulated in production.