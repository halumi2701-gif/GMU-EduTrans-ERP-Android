from pathlib import Path
import re

root = Path('gawone-customer-production')
src = root / 'app/src/main/java/site/garsyanimultiusaha/gawone'
main = src / 'MainActivity.kt'
build = root / 'app/build.gradle.kts'

if not main.exists() or not build.exists():
    raise SystemExit('GAWONE Customer source not reconstructed')

mapper = src / 'GawoneCustomerFailure.kt'
mapper.write_text(r'''package site.garsyanimultiusaha.gawone

enum class CustomerRecoveryAction {
    RETRY,
    LOGIN_AGAIN,
    OPEN_ORDERS,
    CHANGE_SCHEDULE,
    CONTACT_SUPPORT,
    NONE
}

data class CustomerFailureUi(
    val code: String,
    val title: String,
    val message: String,
    val retryable: Boolean,
    val primaryAction: CustomerRecoveryAction,
    val secondaryAction: CustomerRecoveryAction = CustomerRecoveryAction.NONE
)

object GawoneCustomerFailureMapper {
    private fun normalized(raw: String?): String = raw.orEmpty().trim().uppercase()

    fun map(raw: String?, fallback: String = "Terjadi kendala"): CustomerFailureUi {
        val value = normalized(raw)

        fun has(vararg tokens: String) = tokens.any { value.contains(it) }
        fun unavailable(code: String, title: String, message: String) = CustomerFailureUi(
            code, title, message, true, CustomerRecoveryAction.RETRY
        )

        if (has("AUTH_REQUIRED", "SESSION_EXPIRED", "JWT EXPIRED", "PGRST301", "HTTP 401", "HTTP 403")) {
            return CustomerFailureUi(
                "SESSION_EXPIRED",
                "Sesi berakhir",
                "Masuk kembali untuk melanjutkan. Pesanan yang sudah tersimpan tetap aman.",
                false,
                CustomerRecoveryAction.LOGIN_AGAIN
            )
        }
        if (has("TIMEOUT", "TIMED OUT", "SOCKETTIMEOUT")) {
            return CustomerFailureUi(
                "REQUEST_TIMEOUT",
                "Koneksi terlalu lama",
                "Permintaan belum selesai. Coba lagi tanpa membuat pesanan ganda.",
                true,
                CustomerRecoveryAction.RETRY
            )
        }
        if (has("UNABLE TO RESOLVE HOST", "FAILED TO CONNECT", "NETWORK IS UNREACHABLE", "NO INTERNET", "NETWORK_OFFLINE")) {
            return CustomerFailureUi(
                "NETWORK_OFFLINE",
                "Tidak ada koneksi internet",
                "Periksa koneksi lalu coba lagi. Status pesanan akan disinkronkan kembali.",
                true,
                CustomerRecoveryAction.RETRY
            )
        }
        if (has("NO_PARTNER_AVAILABLE")) {
            return CustomerFailureUi(
                "NO_PARTNER_AVAILABLE",
                "Mitra belum tersedia",
                "Belum ada Mitra yang dapat menerima pesanan saat ini. Coba lagi atau ubah jadwal.",
                true,
                CustomerRecoveryAction.RETRY,
                CustomerRecoveryAction.CHANGE_SCHEDULE
            )
        }
        if (has("ESTIMATE_EXPIRED", "PRICE_ESTIMATE_EXPIRED", "ESTIMATE_NOT_ACTIVE")) {
            return CustomerFailureUi(
                "ESTIMATE_EXPIRED",
                "Estimasi harga perlu diperbarui",
                "Hitung ulang estimasi harga sebelum melanjutkan.",
                true,
                CustomerRecoveryAction.RETRY
            )
        }
        if (has("CUSTOMER_BOOKING_FEATURE_DISABLED", "SERVICE_BOOKING_DISABLED")) {
            return unavailable(
                "BOOKING_UNAVAILABLE",
                "Pemesanan sementara belum tersedia",
                "Layanan pemesanan sedang disiapkan. Coba lagi beberapa saat lagi."
            )
        }
        if (has("CUSTOMER_MATCHING_FEATURE_DISABLED", "SERVICE_MATCHING_DISABLED")) {
            return unavailable(
                "MATCHING_UNAVAILABLE",
                "Pencarian Mitra sementara belum tersedia",
                "Pesanan tetap aman. Coba cari Mitra lagi beberapa saat lagi."
            )
        }
        if (has("PAYMENT_UNAVAILABLE", "CUSTOMER_PAYMENT_FEATURE_DISABLED", "PAYMENT_FEATURE_DISABLED")) {
            return CustomerFailureUi(
                "PAYMENT_UNAVAILABLE",
                "Pembayaran online belum tersedia",
                "Pilih metode pembayaran yang tersedia atau coba lagi nanti.",
                false,
                CustomerRecoveryAction.NONE
            )
        }
        if (has("MAPS_UNAVAILABLE", "CUSTOMER_MAPS_FEATURE_DISABLED", "MAPS_FEATURE_DISABLED")) {
            return CustomerFailureUi(
                "MAPS_UNAVAILABLE",
                "Peta belum tersedia",
                "Gunakan lokasi yang sudah tersimpan. Fitur peta akan aktif setelah layanan peta terhubung.",
                false,
                CustomerRecoveryAction.NONE
            )
        }
        if (has("PUSH_UNAVAILABLE", "CUSTOMER_PUSH_FEATURE_DISABLED", "PUSH_FEATURE_DISABLED")) {
            return CustomerFailureUi(
                "PUSH_UNAVAILABLE",
                "Notifikasi belum tersedia",
                "Pantau status pesanan langsung dari aplikasi.",
                false,
                CustomerRecoveryAction.OPEN_ORDERS
            )
        }
        if (has("ORDER_NOT_FOUND")) {
            return CustomerFailureUi(
                "ORDER_NOT_FOUND",
                "Pesanan tidak ditemukan",
                "Pesanan mungkin sudah berubah. Muat ulang daftar pesanan untuk melihat status terbaru.",
                false,
                CustomerRecoveryAction.OPEN_ORDERS
            )
        }
        if (has("ORDER_CANNOT_BE_CANCELLED", "ORDER_NOT_CANCELLABLE")) {
            return CustomerFailureUi(
                "ORDER_CANNOT_BE_CANCELLED",
                "Pesanan tidak dapat dibatalkan",
                "Status pesanan sudah terlalu jauh untuk dibatalkan dari aplikasi. Hubungi Bantuan jika diperlukan.",
                false,
                CustomerRecoveryAction.OPEN_ORDERS,
                CustomerRecoveryAction.CONTACT_SUPPORT
            )
        }
        if (has("PERMISSION DENIED FOR TABLE", "PERMISSION_DENIED", "42501", "PGRST", "POSTGREST", "SQLSTATE", "DATABASE ERROR")) {
            return CustomerFailureUi(
                "SERVICE_TEMPORARILY_UNAVAILABLE",
                "Layanan sedang diperbarui",
                "Coba lagi beberapa saat lagi. Pesanan tidak akan dibuat ganda.",
                true,
                CustomerRecoveryAction.RETRY,
                CustomerRecoveryAction.CONTACT_SUPPORT
            )
        }

        return CustomerFailureUi(
            "REQUEST_FAILED",
            fallback,
            "Kami belum dapat menyelesaikan permintaan ini. Coba lagi. Jika tetap terjadi, buka Bantuan.",
            true,
            CustomerRecoveryAction.RETRY,
            CustomerRecoveryAction.CONTACT_SUPPORT
        )
    }

    fun message(raw: String?, fallback: String = "Terjadi kendala"): String = map(raw, fallback).message
}

fun customerSafeError(error: Throwable?, fallback: String = "Terjadi kendala"): String =
    GawoneCustomerFailureMapper.message(error?.message, fallback)
''')

# Replace the common pattern that leaked backend/HTTP/SQL exception text into Compose UI.
for path in src.rglob('*.kt'):
    if path.name in {'SupabaseApi.kt', 'GawoneCustomerFailure.kt'}:
        continue
    text = path.read_text()
    original = text
    text = re.sub(
        r'([A-Za-z_][A-Za-z0-9_]*)\.message\s*\?:\s*"([^"]*)"',
        lambda m: f'customerSafeError({m.group(1)}, "{m.group(2)}")',
        text,
    )
    text = re.sub(
        r'([A-Za-z_][A-Za-z0-9_]*)\.localizedMessage\s*\?:\s*"([^"]*)"',
        lambda m: f'customerSafeError({m.group(1)}, "{m.group(2)}")',
        text,
    )
    if text != original:
        path.write_text(text)

# RC8 showed an enum-like status code in the active-order chip. Always show customer copy instead.
s = main.read_text()
s = s.replace(
    'Text(order.status,fontSize=8.sp,fontWeight=FontWeight.Bold,color=GGreen)',
    'Text(statusCopy(order.status).first,fontSize=8.sp,fontWeight=FontWeight.Bold,color=GGreen)'
)
main.write_text(s)

# Version bump.
b = build.read_text()
if 'versionCode = 17' not in b or 'versionName = "1.0.7-uiux-v3"' not in b:
    raise SystemExit('RC8 version anchor missing')
b = b.replace('versionCode = 17', 'versionCode = 18', 1)
b = b.replace('versionName = "1.0.7-uiux-v3"', 'versionName = "1.0.8-reliability-rc9"', 1)
build.write_text(b)

# Fail closed if a direct exception message is still used as an immediate fallback in UI code.
leaks = []
for path in src.rglob('*.kt'):
    if path.name in {'SupabaseApi.kt', 'GawoneCustomerFailure.kt'}:
        continue
    text = path.read_text()
    if re.search(r'\.message\s*\?:\s*"', text) or re.search(r'\.localizedMessage\s*\?:\s*"', text):
        leaks.append(str(path))
if leaks:
    raise SystemExit('raw exception UI leak remains: ' + ', '.join(leaks))

if 'versionCode = 18' not in build.read_text():
    raise SystemExit('RC9 version bump failed')
if 'GawoneCustomerFailureMapper' not in mapper.read_text():
    raise SystemExit('failure mapper missing')

print('GAWONE Customer RC9 reliability hardening applied')
