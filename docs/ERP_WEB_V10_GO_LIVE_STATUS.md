# GMU EduTrans ERP Web v10 — Go-Live Status

Status date: 2026-09-14

## Production baseline
- Project: `gmu-edutrans-erp`
- Vercel project ID: `prj_3enHdsSqMjcLHQCcXPLyZI8UXS9Q`
- Vercel team ID: `team_eb0gRoeVYnxOU49hZdWSUqYF`
- Production domain: `https://erp.edutrans.garsyanimultiusaha.site/`
- Current production deployment: `dpl_Cd5WygS7HdbPyAb2BcWkANcoBEQe`
- Current live baseline: ERP Web v9.5
- Runtime errors in latest 24h at audit time: none detected

## ERP Web v10 release candidate
Validated modules:
- v9.6 Media Master
- v9.7 Package Master
- v9.8 Manager Ops Agent
- v9.9 Role & Privacy Sync
- v10 Unified Loader

Finance privacy contract:
- Full company finance: Owner / Director / Direktur only
- Manager / Manager EduTrans: operational access only; no full company finance

Release candidate artifact:
- Name: `gmu-edutrans-erp-web-v10-release-candidate`
- Artifact ID: `10334645888`
- SHA256: `0864420b2d7092ab6a5ca238ecfdf234c0f9a12b7de476ec3199e223088b6c27`
- CI run: `34809822537`
- Result: SUCCESS

The release bundle is built from the current live v9.5 HTML, then injects v10 additively. Login, Trip Folder, Document Center, existing layout, and the v9.5 baseline are preserved.

## Production deployment workflow
Workflow: `.github/workflows/deploy-erp-web-v10.yml`

It is pinned to the exact ERP Vercel project and production domain. It validates v10 contracts, rebuilds from the current live v9.5 baseline, links the exact project, deploys, and verifies production markers.

## Current blockers
1. Supabase connector is currently disabled at the ChatGPT connector layer. No production database write has occurred. Media Sync backend must be activated before enabling ERP v10 Media Master in production.
2. Vercel deployment from GitHub requires `VERCEL_TOKEN` in the GitHub `production` environment. The available Vercel chat action does not accept an explicit project ID, so it must not be used blindly while multiple GMU projects exist.

## Required activation order
1. Restore Supabase connector access for project `gtgnwasijweewmaubvyg`.
2. Confirm existing migrations/functions to avoid duplicate DDL.
3. Apply prepared media migrations and deploy `internal-media-master` plus `public-package-catalog` v10.
4. Verify station package contract remains Rp46.000/pax, minimum 20, exact 10 facilities, and no internal-finance leakage.
5. Run Supabase security advisors after DDL.
6. Ensure GitHub production environment has `VERCEL_TOKEN`.
7. Run `Deploy ERP Web v10` with `confirm_production=DEPLOY`.
8. Verify production domain keeps v9.5 baseline markers plus v10 additive marker and loader.
9. Verify Owner/Director finance, Manager operational-only, Package Master, Media Master, and Ops Agent.

## Rollback
Primary rollback candidate is the current stable production deployment:
`dpl_Cd5WygS7HdbPyAb2BcWkANcoBEQe`

Do not replace or delete this rollback reference until ERP Web v10 has passed production acceptance.
