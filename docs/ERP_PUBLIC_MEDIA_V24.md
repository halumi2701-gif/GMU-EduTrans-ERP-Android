# GMU EduTrans — ERP → Customer Web Media Sync v24

## Tujuan
Semua media publik Program dan Paket dikelola dari ERP dan otomatis tampil di web customer. Tidak ada lagi gambar program/paket yang harus di-hardcode di frontend.

## Program Master
Field publik:
- Cover Image
- Gallery Images (maks. 5)
- Nama program
- Kategori
- Deskripsi publik
- Minimum pax
- Status publik

## Package Master
Field publik:
- Cover Image
- Gallery Images (maks. 5)
- Nama paket
- Deskripsi publik
- Harga jual / pax
- Minimum pax
- Fasilitas / Isi Paket
- Effective date
- Status ACTIVE / DRAFT

## UX ERP
Akses edit media: Owner, Manager, Admin.

Kontrol:
1. Upload Cover
2. Tambah Galeri
3. Preview gambar
4. Ganti Cover
5. Hapus gambar
6. Urutkan galeri
7. Simpan sebagai DRAFT
8. Publish/Activate mengikuti approval paket/program

Format yang diizinkan:
- JPEG
- PNG
- WebP
- maksimal 8 MB / file

## Storage
Bucket: `public-catalog-media`

Path:
- `programs/<program_id>/cover.<ext>`
- `programs/<program_id>/gallery/<uuid>.<ext>`
- `packages/<package_id>/cover.<ext>`
- `packages/<package_id>/gallery/<uuid>.<ext>`

Public read diperbolehkan karena media memang untuk website customer. Upload/update/delete hanya user ERP berizin.

## Data Contract
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
  "gallery_urls": ["https://...", "https://..."]
}
```

## Customer Web
- Program card memakai `cover_image_url` dari ERP.
- Package card memakai `cover_image_url` dari ERP.
- Gallery ditampilkan bila tersedia.
- Bila gambar belum ada, frontend memakai fallback GMU.
- Tidak ada HPP, fee internal, margin, profit, atau pricing note internal di response publik.

## Publish Flow
ERP → upload media → Storage → simpan URL ke Program/Package Master → approval/ACTIVE → public-package-catalog → customer web.
