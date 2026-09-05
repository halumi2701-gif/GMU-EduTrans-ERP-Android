# GMU EduTrans ERP — Android Native v0.1

Native Android proof-of-concept. **Tidak menggunakan WebView.**

## Scope v0.1
- Kotlin + Jetpack Compose
- Login langsung ke Supabase Auth
- Membaca profil/role dari public.profiles
- Menolak akun is_active=false
- Session lokal untuk pengujian
- Role-aware native navigation
- User & Role hanya untuk Owner
- Logout
- Package: com.garsyanimultiusaha.gmuedutrans.erp

## Build
JDK 17, Android SDK 35, Gradle 8.7.

Build debug APK:

```bash
gradle assembleDebug
```

Output:
`app/build/outputs/apk/debug/app-debug.apk`
