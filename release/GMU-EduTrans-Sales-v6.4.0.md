# GMU EduTrans Sales v6.4.0 — Internal Production Release

Release date: 2026-09-17
Branch: `sales-app-v1`
Application source commit: `4fa7183790b1d354abae6611c1bbdcc4ff61e609`
Backend security hardening commit: `46ee6551947ea8d38cb15b50897df0d98627af1b`
Application ID: `com.garsyanimultiusaha.gmuedutrans.sales`
Version code: `10`
Version name: `6.4.0`

## Build verification

- Sales debug workflow run #73: SUCCESS
- Sales release workflow run #6: SUCCESS
- Release candidate artifact: `GMU-EduTrans-Sales-v6.4.0-release-candidate`
- Unsigned build originated from GitHub Actions release workflow.

## Backend E2E health

Production backend checks returned all green on 2026-09-17:

- quotation decision: ready
- payment → WON: ready
- invoice → handover: ready
- booking request handover progress: ready
- ERP booking status → request sync: ready
- 20-pax commission block calculation: ready
- onboarding: ready
- attendance / visit / daily report / special price field ops: ready

Overall backend health: `true` (8/8 critical integration groups).

## Security hardening

Direct API execution was revoked from `anon` and ordinary `authenticated` roles for three trigger-only SECURITY DEFINER functions:

- `internal_recalc_invoice_from_payment()`
- `internal_sales_sync_dp_handover()`
- `internal_sync_sales_handover_from_booking_request()`

The production Supabase security advisor no longer reports the anonymous SECURITY DEFINER execution warning for these functions. Remaining authenticated SECURITY DEFINER warnings are expected for role-checked application RPCs and should continue to be reviewed when new RPCs are added.

## Internal production signing

A dedicated GMU EduTrans Sales signing identity was generated for internal APK distribution and future in-place updates.

Certificate subject:
`CN=GMU EduTrans Sales, OU=EduTrans, O=PT Garsyani Multi Usaha, L=Cianjur, ST=Jawa Barat, C=ID`

Certificate SHA-256 fingerprint:
`DF:1A:04:A1:9B:29:BD:0F:9F:64:45:0A:75:1E:B7:B1:FF:E0:5B:95:8E:67:A8:66:55:DD:B8:9D:D9:F1:04:CD`

Signed APK SHA-256:
`c1998aaec8b8fb36589a4244b984a00a3058ac56032885f006dac82a6de06837`

The signing key and recovery record are stored privately in the Owner's Google Drive folder `GMU EduTrans Production Signing`. Secrets are intentionally not committed to this repository.

## Distribution status

This release is intended for internal GMU EduTrans sideload distribution. It is not a Google Play Store submission artifact. Future updates must use the same signing key.
