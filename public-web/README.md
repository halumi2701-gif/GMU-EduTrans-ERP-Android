# GMU EduTrans Public Web v23

Source permanen untuk peningkatan bagian **Paket & Harga** pada web customer GMU EduTrans.

## Tujuan v23

- Program Master tetap tersinkron dari ERP.
- Paket ACTIVE tampil otomatis saat halaman dibuka.
- Filter Program / Tanggal / Pax tetap tersedia.
- Kartu paket menampilkan harga jual, minimum peserta, durasi (dibaca dari facilities), isi paket sebagai checklist, estimasi total, dan tombol Pilih Paket.
- Booking/account/customer portal tidak diubah oleh renderer ini.
- Data internal HPP, fee Manager, Sales, Mitra, laba, dan margin tidak boleh dirender di public web.

## File

- `package-renderer-v23.js` — renderer kartu Paket & Harga.
- `package-renderer-v23.css` — styling checklist/meta/estimasi.
- `package-search-v23.js` — mengganti renderer pencarian paket lama tanpa mengubah fungsi booking.
- `package-autoload-v23.js` — memuat katalog paket ACTIVE saat halaman dibuka.
- `package-v23-preview.html` — preview mandiri untuk validasi visual.
- `vercel.json` — baseline konfigurasi Vercel aman.

## Urutan load pada homepage

```html
<link rel="stylesheet" href="/package-renderer-v23.css">
<script src="/package-renderer-v23.js"></script>
<script src="/package-search-v23.js"></script>
<script src="/package-autoload-v23.js"></script>
```

Load file tersebut **setelah** script utama v22 agar `api()`, `choosePackage()`, dan elemen DOM lama tetap dipakai.

## Endpoint sumber data

Homepage tetap menggunakan `public-package-catalog` melalui `/api/proxy?slug=public-package-catalog`.

Public response hanya boleh berisi data customer-safe seperti selling price, minimum pax, facilities, deskripsi, tanggal efektif, dan estimasi total. Jangan menambahkan catatan biaya internal ke response publik.

## Paket Edukasi Lingkungan Stasiun

Baseline publik saat v23 disiapkan:

- Rp46.000 / pax
- Minimum 20 peserta
- ±2 jam
- Edukasi stasiun & perkeretaapian
- 6 sesi profesi / narasumber
- Observasi keberangkatan kereta
- Worksheet
- Sertifikat
- Snack
- Dokumentasi
- Pendampingan TL / MC
- Pendampingan staf operasional
- Kegiatan selama ±2 jam

## Deployment

Project Vercel customer web yang aktif saat source ini dibuat belum terhubung ke Git repository. Setelah source web canonical dipindahkan/ditautkan ke Git, set Root Directory project Vercel ke `public-web` atau pindahkan file ini ke source canonical homepage sebelum deploy preview → verify → production.
