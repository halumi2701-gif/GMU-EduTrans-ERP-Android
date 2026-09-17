# GMU EduTrans — Single Source Data Architecture 2026

Status: LOCKED ARCHITECTURE

## Tujuan
Semua aplikasi GMU EduTrans (Owner ERP, Manager, Sales, Finance/Admin, Operations, Tour Leader, Public/Customer portal jika relevan) harus menggunakan satu sumber data dan satu aturan bisnis agar tidak ada data ganda, harga berbeda, atau status yang tidak sinkron.

## Prinsip Utama

### 1. GitHub Repository = Source of Truth untuk CODE & MASTER DEFINITION
Repository `halumi2701-gif/GMU-EduTrans-ERP-Android` menyimpan:
- Source code aplikasi
- Database migrations
- Edge Functions/API contract
- Master pricing definition
- Master B2B rules
- Role & permission rules
- Business logic
- Validation & margin guard
- Deployment configuration
- Documentation

Repository BUKAN database transaksi runtime.

### 2. Supabase ERP = Source of Truth untuk LIVE DATA
Semua data operasional/transaksi harus berada di satu backend Supabase ERP yang sama, termasuk:
- Users/profiles/roles
- Leads
- Customers/schools
- B2B partners
- Programs
- Packages
- Price books
- Pricing tiers
- Quotations
- Bookings
- Payments
- Invoices
- Sales PIC
- Sales commissions
- School cashback
- Partner margin
- Trips
- Operations
- Crew
- Vendors
- Finance
- Payroll/fee
- Documents
- Notifications
- Audit logs

Tidak boleh ada database khusus Sales yang terpisah dari ERP.

## Sales App Contract
Sales App WAJIB membaca data live dari backend ERP yang sama untuk:
- Daftar program aktif
- Harga PUBLIC
- Harga B2B
- Partner B2B
- Customer/sekolah
- Lead
- Quotation
- Booking
- Pax
- Status pembayaran yang boleh dilihat Sales
- Sales PIC
- Komisi Sales
- Target Sales
- Pipeline
- Follow-up

Sales App tidak boleh menggunakan hardcoded harga jika master price tersedia di backend.

## B2B Station Program Contract
Program: `EDU-STATION-PROF`

Sales memilih channel:
- PUBLIC
- B2B

Jika B2B:
1. Pilih Partner B2B aktif
2. Input/pilih sekolah
3. Input pax
4. Backend menentukan tier harga net otomatis
5. Backend menghitung school selling price
6. Backend menghitung school cashback
7. Backend menghitung partner margin
8. Backend menghitung Sales commission
9. Backend menjalankan margin guard GMU
10. Quotation/booking menyimpan pricing snapshot

Master B2B:
- 40–59 pax: net Rp45.000/pax
- 60–79 pax: net Rp44.500/pax
- 80–99 pax: net Rp44.000/pax
- 100+ pax: net Rp43.500/pax
- School selling price: Rp49.500/pax
- School cashback: Rp2.500/pax
- Sales commission: Rp5.000/pax
- GMU minimum margin: 25%

## Synchronization Rule
Setiap perubahan data oleh Sales harus langsung menjadi data ERP.
Contoh:
Sales membuat lead -> Manager dapat melihat lead yang sama.
Sales membuat quotation -> Owner/Manager melihat quotation yang sama.
Customer membayar -> Finance memperbarui pembayaran -> Sales melihat status yang diizinkan.
Booking confirmed -> Operations menerima booking yang sama.
Trip selesai -> Finance/Owner menerima actual cost dan profitability dari booking yang sama.

Tidak ada export-import manual antar modul untuk proses normal.

## Access Control
Data yang sama tidak berarti semua role melihat semua field.

- Owner/Director: full access
- Manager: operational + approved financial access
- Sales: lead/customer/program/price/quotation/booking/target/commission sesuai scope, tanpa internal HPP/profit sensitif
- Admin/Finance: administrasi dan finance sesuai scope
- Operations/TL: hanya data trip/crew/rundown/operational yang ditugaskan

Backend/RLS/API harus menjadi enforcement utama, bukan hanya menyembunyikan UI.

## No Local Master Rule
Dilarang menjadikan data berikut sebagai master lokal permanen di Sales App:
- Harga
- B2B tiers
- Cashback
- Commission rates
- Partner status
- Program status
- Customer master
- Booking status

Local cache boleh digunakan hanya untuk performa/offline sementara dan harus sinkron kembali ke backend ERP.

## Deployment Rule
Perubahan master atau logic dilakukan melalui repository -> migration/config/code -> deploy -> semua aplikasi memakai backend/version yang sama.

## Final Architecture

GitHub Repository
  -> code + migrations + master rules + deployment
  -> deploy

Single Supabase ERP Backend
  -> live shared data
  -> shared APIs/RPC/Edge Functions

Sales App / Owner ERP / Manager / Finance / Operations / TL
  <-> membaca dan menulis ke backend ERP yang sama

Hasil akhir: satu data, satu harga, satu customer, satu booking, satu status, satu audit trail.
