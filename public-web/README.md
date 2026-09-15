# GMU EduTrans Public Web v25

Source permanen untuk **Paket & Harga + Customer Account Gate** pada web customer GMU EduTrans.

## Tujuan

- Program Master tetap tersinkron dari ERP.
- Paket ACTIVE tampil otomatis saat halaman dibuka.
- Customer boleh melihat program/paket tanpa login.
- Customer **wajib daftar / login sebelum order paket**.
- Signup membuat `customer_accounts` di ERP sehingga prospek tetap terlihat walaupun belum jadi booking.
- Paket yang dipilih dicatat sebagai minat terakhir customer.
- Booking baru dibuat saat customer benar-benar melanjutkan order; signup tidak membuat booking palsu.
- Data internal HPP, fee Manager, Sales, Mitra, laba, dan margin tidak boleh dirender di public web.

## File

- `package-renderer-v23.js` — renderer kartu Paket & Harga.
- `package-renderer-v23.css` — styling checklist/meta/estimasi.
- `package-search-v23.js` — pencarian paket + integrasi Customer Account Gate.
- `package-autoload-v23.js` — memuat katalog paket ACTIVE saat halaman dibuka.
- `customer-account-gate-v25.js` — auth gate, signup/login helper, pencatatan minat paket, resume order setelah login.
- `customer-account-ui-v25.js` — modal Daftar / Masuk untuk customer.
- `package-v23-preview.html` — preview mandiri untuk validasi visual.
- `vercel.json` — baseline konfigurasi Vercel aman.

## Flow customer

`Lihat paket → Pilih Paket → cek session → belum login: Daftar/Masuk → akun masuk ERP → minat paket dicatat → lanjut booking → Booking Request terhubung ke customer_account_id`.

Customer yang baru daftar tetapi belum booking tetap muncul di **Customer Account & Prospek Web** pada ERP. Sales hanya boleh melakukan follow-up bila `followup_consent = true`.

## Urutan load pada homepage

```html
<link rel="stylesheet" href="/package-renderer-v23.css">
<script src="/package-renderer-v23.js"></script>
<script src="/customer-account-gate-v25.js"></script>
<script src="/customer-account-ui-v25.js"></script>
<script src="/package-search-v23.js"></script>
<script src="/package-autoload-v23.js"></script>
```

Setelah Supabase browser client dibuat oleh homepage, konfigurasi gate tanpa pernah memakai `service_role` / secret key di browser:

```js
GMU_CUSTOMER_ACCOUNT_GATE_V25.configure({
  supabaseClient: publicSupabase,
  programResolver: (packageId) => document.getElementById('programSel')?.value || null,
  packageResolver: (packageId) => packageId
});
```

Gunakan publishable/anon client key sesuai konfigurasi project dan RLS. `service_role` tidak boleh berada di public web.

## Endpoint sumber data

Homepage tetap menggunakan `public-package-catalog` melalui `/api/proxy?slug=public-package-catalog` untuk katalog customer-safe. Auth menggunakan Supabase Auth client yang dikonfigurasi homepage. Pencatatan minat customer menggunakan RPC `gmu_customer_account_touch_interest` setelah user terautentikasi.

## Database / ERP

Migration `gmu_v220_customer_account_crm` menambahkan:

- `public.customer_accounts`
- trigger signup dari `auth.users`
- `booking_requests.customer_account_id`
- RLS customer/staff
- RPC customer-safe untuk mencatat minat
- sinkronisasi status akun saat Booking Request dibuat / dikonversi menjadi booking

ERP memuat `customer-account-crm-v220.js` untuk menampilkan akun baru, akun belum booking, izin follow-up, minat paket, Sales PIC, dan jadwal follow-up.

## Deployment

Project Vercel customer web yang aktif saat source ini dibuat **belum terhubung ke Git repository**. Karena itu perubahan di folder `public-web` adalah source canonical tetapi belum otomatis live. Setelah source homepage aktif dipindahkan/ditautkan ke Git, load file v25 di atas lalu deploy preview → verifikasi signup/login/order → production.
