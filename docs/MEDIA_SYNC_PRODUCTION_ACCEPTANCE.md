# GMU EduTrans — Media Sync Production Acceptance & Rollback

## Status Gate
Media Sync production hanya boleh diaktifkan bila seluruh syarat berikut terpenuhi:
- Debug APK v5.0.0-alpha9 SUCCESS.
- Release APK v5.0.0-alpha9 SUCCESS.
- Public web renderer contract SUCCESS.
- Media Sync database/storage contract SUCCESS.
- Live catalog baseline lama masih sehat sebelum deployment.

## Baseline Publik yang Wajib Tetap Sama
Paket `PKG-GMU-00008` — Edukasi Lingkungan Stasiun:
- Harga: Rp46.000/pax.
- Minimum: 20 peserta.
- 20 peserta = Rp920.000.
- Tepat 10 fasilitas publik.
- 19 peserta tidak boleh mendapatkan paket aktif.
- `custom_trip_available`, `as_of`, dan `programs[].sort_order` tetap tersedia untuk kompatibilitas frontend lama.
- Booking tetap meneruskan package ID; media tidak menjadi bagian dari payload booking.

## Urutan Aktivasi Production
1. Verifikasi state production terlebih dahulu.
2. Dry-run migration.
3. Terapkan schema media pada `programs` dan `program_packages`.
4. Pastikan bucket `edutrans-media` tersedia dengan:
   - public read;
   - max file 8 MB;
   - MIME JPEG/PNG/WebP;
   - upload hanya role ERP berizin.
5. Deploy `internal-media-master`.
6. Uji login role Owner/Director/Manager EduTrans/Admin untuk load/save media.
7. Deploy `public-package-catalog` v10-media-sync.
8. Jalankan `scripts/verify-media-catalog.sh`.
9. Uji web customer dengan paket Rp46.000.
10. Uji upload cover + 5 gallery dari ERP dan pastikan tampil di web customer.
11. Uji ganti cover, jadikan gallery sebagai cover, urutkan, hapus, dan cleanup file lama.
12. Uji booking dari paket yang memiliki media; booking harus tetap memakai package ID yang sama.

## Acceptance Criteria
Deployment dinyatakan LULUS hanya bila:
- response catalog memiliki `version = v10-media-sync`;
- `catalog_status = AVAILABLE` untuk 20 peserta;
- `PKG-GMU-00008` = Rp46.000/pax;
- minimum pax = 20;
- fasilitas tepat 10;
- estimated total = Rp920.000;
- 19 peserta tidak mendapat paket aktif;
- Program dan Paket memiliki field `cover_image_url` dan `gallery_urls`;
- gallery maksimal 5 item;
- media publik hanya HTTPS/null;
- package cover mengalahkan Program fallback bila tersedia;
- jika package cover kosong, `program_cover_image_url` dapat dipakai sebagai fallback;
- `custom_trip_available`, `as_of`, dan `programs[].sort_order` tetap tersedia;
- tidak ada `price_note`, HPP/base_cost, manager/sales/mitra fee, profit, margin, pricing policy, atau cost template di seluruh response publik;
- customer dapat memilih paket dan alur booking tetap normal.

## Rollback Trigger
Rollback WAJIB dilakukan bila salah satu terjadi:
- paket Rp46.000 hilang pada 20 peserta;
- minimum pax berubah;
- 10 fasilitas berubah/terpotong;
- endpoint public mengeluarkan field finansial internal;
- booking tidak lagi menerima package ID yang benar;
- public catalog error 5xx setelah deployment;
- Manager/role internal gagal menggunakan Media Master;
- media package/program tidak dapat dibaca customer web;
- kontrak frontend v9 (`custom_trip_available`, `as_of`, `sort_order`) hilang.

## Rollback Procedure
1. Hentikan promosi perubahan frontend baru.
2. Kembalikan `public-package-catalog` ke versi production terakhir yang sehat.
3. Kembalikan `internal-media-master` bila error berasal dari fungsi internal.
4. Jangan hapus kolom media jika schema sudah terpasang; kolom nullable aman dibiarkan untuk forward compatibility.
5. Jika policy Storage bermasalah, nonaktifkan write policy yang baru dan pertahankan public read hanya bila diperlukan.
6. Verifikasi ulang baseline production:
   - Rp46.000/pax;
   - minimum 20;
   - 10 fasilitas;
   - Rp920.000 untuk 20 peserta;
   - 19 peserta ditolak;
   - tidak ada data finansial internal.
7. Baru buka kembali deployment setelah penyebab kegagalan diperbaiki dan seluruh contract test hijau.

## Prinsip Akses
- Owner/Director/Manager EduTrans/Admin: kelola media sesuai role policy.
- Customer/public: read-only media dan data paket publik.
- Data keuangan internal tetap Owner/Manager only dan tidak pernah ikut public catalog.

## Source of Truth
- Migration: `supabase/migrations/20260912153500_add_program_package_media.sql`
- Storage: `supabase/migrations/20260912153800_create_edutrans_media_bucket.sql`
- Internal API: `supabase/functions/internal-media-master/index.ts`
- Public API: `supabase/functions/public-package-catalog/index.ts`
- Renderer test: `scripts/verify-public-web-media.js`
- Catalog smoke test: `scripts/verify-media-catalog.sh`
- Deploy workflow: `.github/workflows/deploy-supabase-media.yml`
