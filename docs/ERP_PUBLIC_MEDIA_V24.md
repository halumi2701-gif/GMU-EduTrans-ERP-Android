# GMU EduTrans — ERP → Customer Web Media Sync v24

## Tujuan
Semua media publik Program dan Paket dikelola dari ERP dan otomatis tampil di web customer. Tidak ada lagi gambar Program/Paket yang harus di-hardcode di frontend.

## Program Master
Field publik:
- Cover Image
- Gallery Images, maksimum 5
- Nama program
- Kategori
- Deskripsi publik
- Minimum pax
- Status publik

## Package Master
Field publik:
- Cover Image
- Gallery Images, maksimum 5
- Nama paket
- Deskripsi publik
- Harga jual / pax
- Minimum pax
- Fasilitas / Isi Paket
- Effective date
- Status ACTIVE / DRAFT

## Hak Akses ERP
Media publik dapat dikelola oleh role aktif berikut:
- Owner
- Director / Direktur
- Manager
- Manager EduTrans
- Admin

Pengelolaan media **tidak** memberikan akses ke HPP, margin, profit, fee Manager/Sales/Mitra, atau pricing policy.

## UX ERP
Kontrol Media Master:
1. Upload Cover
2. Tambah Galeri
3. Preview gambar
4. Jadikan foto galeri sebagai Cover
5. Ganti / hapus Cover
6. Hapus foto galeri
7. Urutkan galeri
8. Simpan Media
9. Batal dan bersihkan upload sementara

Format yang diizinkan:
- JPEG
- PNG
- WebP
- maksimal 8 MB / file
- galeri maksimal 5 file per Program/Paket

## Storage
Bucket production: `edutrans-media`

Bucket bersifat **public-read** karena media memang ditampilkan di website customer. Upload tetap membutuhkan user ERP authenticated dengan role yang diizinkan.

Path file:
- `program/<program_id>/<uuid>.<ext>`
- `package/<package_id>/<uuid>.<ext>`

Setiap URL yang disimpan oleh `internal-media-master` harus berasal dari bucket `edutrans-media` dan folder entity yang sedang diedit. URL eksternal dan cross-entity ditolak.

File lama yang tidak lagi dipakai dibersihkan server-side setelah save. File baru yang sudah diupload tetapi dibatalkan juga dapat dibersihkan melalui action `discard`.

## Database Contract
Kolom pada `programs` dan `program_packages`:
- `cover_image_url text`
- `gallery_urls jsonb not null default []`

Constraint:
- Cover, jika ada, harus HTTPS.
- Gallery wajib JSON array.
- Gallery maksimal 5 item.

## Data Contract Publik
Program public object:
```json
{
  "id": "...",
  "name": "...",
  "cover_image_url": "https://...",
  "gallery_urls": ["https://...", "https://..."]
}
```

Package public object:
```json
{
  "id": "...",
  "name": "...",
  "price_per_pax": 46000,
  "min_pax": 20,
  "facilities": ["..."],
  "cover_image_url": "https://...",
  "program_cover_image_url": "https://...",
  "gallery_urls": ["https://...", "https://..."]
}
```

## Customer Web
- Program card memakai `cover_image_url` dari ERP.
- Package card memakai `cover_image_url` paket; jika kosong memakai `program_cover_image_url`.
- Paket dapat menampilkan Cover + seluruh 5 foto galeri sebagai thumbnail interaktif.
- `Pilih Paket` meneruskan **package ID saja** ke booking flow; media tidak masuk payload booking.
- Fallback GMU digunakan bila media belum tersedia.
- Tidak ada HPP, fee internal, margin, profit, pricing note, atau pricing policy internal di response publik.

## Paket Baseline Yang Dikunci
`PKG-GMU-00008` — Paket Edukasi Lingkungan Stasiun:
- Rp46.000 / pax
- minimum 20 peserta
- tepat 10 fasilitas / isi paket
- estimasi 20 pax Rp920.000

## Publish Flow
ERP → Media Master → upload ke `edutrans-media` → simpan `cover_image_url` / `gallery_urls` → Program/Package Master → ACTIVE → `public-package-catalog v10-media-sync` → web customer.

Approval / aktivasi paket tetap mengikuti workflow paket yang sudah ada; penggantian media tidak memberikan hak approval finansial.

## Deployment Guard
Sebelum production:
1. `verify-public-web-media.js`
2. `verify-media-migrations.sh`
3. Supabase migration dry-run
4. Apply 2 migration Media Sync
5. Deploy `internal-media-master`
6. Deploy `public-package-catalog`
7. `verify-media-catalog.sh` ke endpoint production

Deployment dianggap lulus hanya bila harga/minimum/fasilitas tetap benar, media valid, dan recursive financial-leak guard lolos.
