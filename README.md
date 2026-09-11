# GMU EduTrans ERP Android

Native Android ERP for GMU EduTrans / PT Garsyani Multi Usaha.

## Current release line

- Android Native / Jetpack Compose
- Supabase-backed ERP data
- Role-based access control
- Booking, customer, operations, vendor, trip folder, workflow, SOP, reports, HR, finance and management controls
- Manager EduTrans role focused on EduTrans daily operations
- GMU EduTrans Ops Agent on the Manager dashboard with trip readiness, operational quick actions, RAB-vs-actual analysis, and authority escalation to Director

## Manager EduTrans authority

Manager EduTrans controls day-to-day EduTrans execution: booking handoff, trip preparation, rundown, manifest, operation sheet, crew, vendor/PO, documents, reports and operational closing. Full-company finance, user administration and strategic company controls remain outside the Manager workspace. Operational actions above Rp2,000,000 or strategic/sensitive actions require Director approval.

## Build

Use the GitHub Actions Android build workflow or Gradle assembleDebug with JDK 17.
