# GMU EduTrans Sales App

Aplikasi Android terpisah untuk tim Sales GMU EduTrans.

## Fokus kerja
- Target utama 400 paid pax / bulan
- CRM lead sekolah / instansi
- Follow-up harian dan HOT lead
- Funnel & forecast pencapaian target
- Katalog program dan paket dari master aktif
- Marketing & Sales Kit: program, materi, script, quotation, funnel
- Company profile ringkas dan brosur teks yang dapat dibagikan
- Draft quotation langsung dari lead memakai harga publik master aktif
- Booking milik Sales sendiri
- Komisi Sales
- Role Sales tanpa akses HPP, laba, saldo, atau laporan strategis perusahaan

## Guardrail quotation
Sales App tidak menerima input harga bebas atau diskon. Draft quotation dibuat melalui RPC backend yang:
- hanya dapat dijalankan akun aktif dengan role `Sales`;
- hanya dapat memakai lead yang memang ditugaskan ke Sales tersebut;
- hanya dapat memakai paket `ACTIVE` dan harga publik master;
- memeriksa minimum pax;
- membuat quotation berstatus `DRAFT` tanpa diskon;
- otomatis memindahkan pipeline ke tahap `QUOTATION` dan menjadwalkan follow-up.

Package: `com.garsyanimultiusaha.gmuedutrans.sales`

Aplikasi ini terpisah dari ERP utama walaupun berada dalam monorepo yang sama. Backend menggunakan project Supabase GMU yang sama dengan akses aman khusus Sales.
