from pathlib import Path

pkg = Path('app/src/main/java/site/garsyanimultiusaha/gawone/management')
main = pkg / 'FullMainActivity.kt'
build = Path('app/build.gradle.kts')

if not main.exists() or not build.exists():
    raise SystemExit('GAWONE Management source missing')

mapper = pkg / 'GawoneManagementFailure.kt'
mapper.write_text(r'''package site.garsyanimultiusaha.gawone.management

data class ManagementFailureUi(
    val code: String,
    val title: String,
    val message: String,
    val retryable: Boolean,
    val loginAgain: Boolean = false
)

object GawoneManagementFailureMapper {
    private fun normalized(raw: String?): String = raw.orEmpty().trim().uppercase()

    fun map(raw: String?): ManagementFailureUi {
        val value = normalized(raw)
        fun has(vararg tokens: String) = tokens.any { value.contains(it) }

        if (has("AUTH_REQUIRED", "SESSION_EXPIRED", "JWT EXPIRED", "INVALID JWT", "PGRST301", "HTTP 401")) {
            return ManagementFailureUi(
                "SESSION_EXPIRED", "Sesi berakhir",
                "Masuk kembali untuk melanjutkan operasi Management.", false, true
            )
        }
        if (has("MANAGEMENT_ROLE_REQUIRED", "MANAGEMENT_PERMISSION_DENIED", "MANAGEMENT_QUEUE_ACCESS_DENIED")) {
            return ManagementFailureUi(
                "ACCESS_DENIED", "Akses tidak tersedia",
                "Akun ini tidak memiliki izin untuk membuka data atau menjalankan aksi tersebut.", false
            )
        }
        if (has("MANAGEMENT_FEATURE_DISABLED")) {
            return ManagementFailureUi(
                "FEATURE_UNAVAILABLE", "Fitur sementara belum tersedia",
                "Fitur ini sedang dinonaktifkan melalui kontrol operasional. Pilih workspace lain atau coba lagi nanti.", false
            )
        }
        if (has("MANAGEMENT_MAINTENANCE_MODE")) {
            return ManagementFailureUi(
                "MAINTENANCE", "Sedang ada pemeliharaan",
                "GAWONE Management sedang diperbarui. Coba kembali setelah pemeliharaan selesai.", true
            )
        }
        if (has("MANAGEMENT_CLIENT_VERSION_UNSUPPORTED", "UPGRADE_REQUIRED")) {
            return ManagementFailureUi(
                "UPDATE_REQUIRED", "Aplikasi perlu diperbarui",
                "Gunakan versi GAWONE Management terbaru untuk melanjutkan.", false
            )
        }
        if (has("MANAGEMENT_RUNTIME_CONFIG_MISSING", "MANAGEMENT_REQUEST_FAILED")) {
            return ManagementFailureUi(
                "BACKEND_TEMPORARILY_UNAVAILABLE", "Layanan Management belum siap",
                "Backend belum dapat melayani permintaan ini. Coba lagi beberapa saat lagi.", true
            )
        }
        if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {
            return ManagementFailureUi(
                "RESOURCE_CHANGED", "Data sudah berubah",
                "Item ini mungkin sudah diproses oleh operator lain. Muat ulang workspace untuk melihat status terbaru.", true
            )
        }
        if (has("IDEMPOTENCY_KEY_CONFLICT", "ACTION_ALREADY_IN_PROGRESS")) {
            return ManagementFailureUi(
                "ACTION_IN_PROGRESS", "Aksi sedang diproses",
                "Jangan kirim ulang. Muat ulang detail untuk memastikan hasil terbaru.", true
            )
        }
        if (has("TIMEOUT", "TIMED OUT", "SOCKETTIMEOUT")) {
            return ManagementFailureUi(
                "REQUEST_TIMEOUT", "Koneksi terlalu lama",
                "Permintaan belum selesai. Coba lagi; backend mencegah aksi ganda.", true
            )
        }
        if (has("UNABLE TO RESOLVE HOST", "FAILED TO CONNECT", "NETWORK IS UNREACHABLE", "NO INTERNET")) {
            return ManagementFailureUi(
                "NETWORK_OFFLINE", "Tidak ada koneksi internet",
                "Periksa koneksi lalu muat ulang. Data operasional akan disinkronkan dari server.", true
            )
        }
        if (has("PERMISSION DENIED FOR TABLE", "SQLSTATE", "POSTGREST", "DATABASE ERROR")) {
            return ManagementFailureUi(
                "SERVICE_TEMPORARILY_UNAVAILABLE", "Layanan sedang diperbarui",
                "Coba lagi beberapa saat lagi. Jika berulang, catat resource yang sedang dibuka untuk audit.", true
            )
        }
        return ManagementFailureUi(
            "REQUEST_FAILED", "Permintaan belum berhasil",
            "Muat ulang dan coba lagi. Backend akan menjaga idempotensi dan audit aksi.", true
        )
    }

    fun message(raw: String?): String = map(raw).message
}

fun managementSafeError(error: Throwable): String = GawoneManagementFailureMapper.message(error.message)
''')

s = main.read_text()
old = '    private fun friendly(t: Throwable): String = (t.message ?: "Terjadi kesalahan.").take(500)'
new = '    private fun friendly(t: Throwable): String = managementSafeError(t)'
if old not in s:
    raise SystemExit('friendly error anchor missing after UIUX V3')
s = s.replace(old, new, 1)
main.write_text(s)

b = build.read_text()
if 'versionCode = 6' not in b or 'versionName = "1.0.4-uiux-v3"' not in b:
    raise SystemExit('UIUX V3 version anchor missing')
b = b.replace('versionCode = 6', 'versionCode = 7', 1)
b = b.replace('versionName = "1.0.4-uiux-v3"', 'versionName = "1.0.5-reliability-v4"', 1)
build.write_text(b)

if 'managementSafeError(t)' not in main.read_text():
    raise SystemExit('safe Management error path missing')
if 'versionCode = 7' not in build.read_text():
    raise SystemExit('Management V4 version bump failed')

print('GAWONE Management reliability V4 applied')
