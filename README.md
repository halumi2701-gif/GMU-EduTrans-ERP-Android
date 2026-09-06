# GMU EduTrans ERP — Android Native v5 Startup Preview

GMU EduTrans ERP berkembang menjadi **GMU EduTrans OS**: operating system mobile untuk educational trip operations.

## v5 Startup Preview
- Native Kotlin + Jetpack Compose, tanpa WebView
- Startup-style Home dashboard
- Hero KPI dan business snapshot
- Needs Attention / action-oriented dashboard
- Next Trip dengan operation readiness
- Quick Actions
- Performance Insights
- Modern icon navigation
- Native splash + exact GMU EduTrans branding
- Booking workspace + Customer 360
- Finance + profitability
- Trip Control Center, Manifest, Attendance, Rundown, Operation Sheet
- Vendor + PO Vendor
- Trip Folder & Documents
- Workflow + Approval + SOP
- Reports + Evaluation + Closing
- Owner-only Team & Access
- Audit Trail + Notifications
- Encrypted session storage
- Role-aware data and UI

Package: `com.garsyanimultiusaha.gmuedutrans.erp`
Version: `5.0.0-alpha4`


## Financial Privacy
- Hanya **Owner** dan **Manager** yang dapat melihat omzet, harga/pax, pembayaran, piutang, RAB, biaya aktual, laba, margin, profitability, dan nominal PO.
- Admin, Sales, Finance, Operation, dan TL mendapatkan tampilan operasional tanpa angka keuangan.
- Data finansial tidak dimuat ke state aplikasi untuk role selain Owner/Manager; harga booking juga di-redact menjadi 0 pada runtime non-financial.


## Team & HR
Khusus Owner & Manager:
- Staff Directory
- Attendance
- Assignment
- KPI & Target
- Performance Review
- Leave / Permission
- Warning / SP
- Training & Certification
- Contract
- Offboarding

Payroll/kompensasi tidak dibuka ke role lain. Aturan finansial tetap hanya Owner dan Manager.


## Startup Alpha 4
- Booking Pipeline ala CRM dengan stage cards dan List View
- Financial Health dashboard khusus Owner/Manager
- Outstanding Receivables dan Collection Rate
- Trip Control Center dengan visual readiness checklist
- More/Workspace berbentuk startup app grid
- Team & HR tetap terintegrasi
- Financial privacy tetap ketat: hanya Owner & Manager
