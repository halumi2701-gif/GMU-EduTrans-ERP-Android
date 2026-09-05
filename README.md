# GMU EduTrans ERP — Android Native v0.2

Aplikasi Android native GMU EduTrans ERP. **Tidak menggunakan WebView.**

## v0.2
- Kotlin + Jetpack Compose
- Login langsung ke Supabase Auth
- Session refresh menggunakan refresh token
- Hak akses berbasis role
- Dashboard native: Booking, Customer, Pax, Omzet, Trip Mendatang, Top Program
- Booking: list, pencarian, tambah booking, nomor booking dari RPC Supabase
- Customer: list, pencarian, tambah customer, kode customer dari RPC Supabase
- Data mengikuti Supabase RLS
- Audit create Customer/Booking dicoba otomatis
- Package: `com.garsyanimultiusaha.gmuedutrans.erp`

## Build

JDK 17, Android SDK 35, Gradle 8.7.

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`
