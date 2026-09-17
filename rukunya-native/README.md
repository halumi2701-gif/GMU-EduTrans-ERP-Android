# RUKUNYA Native v2

Full native Android application built with Kotlin + Jetpack Compose + Room/SQLite. No WebView is used for the primary UI.

## Packages

### FREE — Rp0
- Core Warga & KK
- Iuran bulanan
- Kas masuk/keluar
- Surat dasar
- Pengumuman
- Backup/restore lokal
- Maksimal 30 KK

### Basic — Rp29.000/bulan
Includes FREE plus:
- KK tanpa batas
- Jenis iuran khusus
- Laporan lanjutan
- Export CSV
- Export PDF
- Histori penuh

### Plus — Rp59.000/bulan
Includes Basic plus:
- Multi-admin hingga 3 profil pengurus
- Entitlement RUKUNYA Cloud
- Akses warga / resident access entitlement
- Sinkronisasi lintas perangkat architecture-ready

Cloud endpoints are intentionally not faked in the offline build. The UI clearly shows when the cloud backend is not connected.

### Pro — Rp99.000/bulan
Includes Plus plus:
- WhatsApp reminder template
- Approval surat
- Dashboard RW entitlement
- Auto-reminder entitlement
- Up to 10 admin profiles

## Architecture
- Kotlin 1.9.24
- Jetpack Compose
- Material 3
- Room / SQLite
- Offline-first
- JSON backup/restore using Android Storage Access Framework
- Native PDF report generation
- CSV report export
- Feature-gate / entitlement engine for FREE, Basic, Plus, Pro

## Current commercial gate
The feature matrix and locking behavior are implemented. Paid-plan activation currently exposes a verifier entry point and debug-only package preview. Before commercial release, connect that verifier to the chosen Lynk.id/license activation workflow so users cannot self-upgrade without payment.
