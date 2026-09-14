from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'gawone-mitra-stage4j')
src = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/mitra'
main = src / 'MainActivity.kt'
build = root / 'app/build.gradle.kts'

if not main.exists() or not build.exists():
    raise SystemExit('GAWONE Mitra Stage4J source not reconstructed')

mapper = src / 'GawoneMitraFailure.kt'
mapper.write_text(r'''package site.garsyanimultiusaha.gawone.mitra

enum class MitraRecoveryAction {
    RETRY,
    LOGIN_AGAIN,
    GO_OFFLINE,
    REFRESH_OFFERS,
    REFRESH_ASSIGNMENTS,
    ENABLE_LOCATION,
    UPLOAD_PROOF,
    OPEN_KYC,
    OPEN_SUPPORT,
    NONE
}

data class MitraFailureUi(
    val code: String,
    val title: String,
    val message: String,
    val retryable: Boolean,
    val primaryAction: MitraRecoveryAction,
    val secondaryAction: MitraRecoveryAction = MitraRecoveryAction.NONE
)

object GawoneMitraFailureMapper {
    private fun normalized(raw: String?): String = raw.orEmpty().trim().uppercase()

    fun map(raw: String?, fallback: String = "Terjadi kendala"): MitraFailureUi {
        val value = normalized(raw)
        fun has(vararg tokens: String) = tokens.any { value.contains(it) }

        if (has("AUTH_REQUIRED", "SESSION_EXPIRED", "JWT EXPIRED", "PGRST301", "HTTP 401", "HTTP 403")) {
            return MitraFailureUi(
                "SESSION_EXPIRED", "Sesi berakhir",
                "Masuk kembali untuk melanjutkan. Status order akan disinkronkan dari server.",
                false, MitraRecoveryAction.LOGIN_AGAIN
            )
        }
        if (has("TIMEOUT", "TIMED OUT", "SOCKETTIMEOUT")) {
            return MitraFailureUi(
                "REQUEST_TIMEOUT", "Koneksi terlalu lama",
                "Permintaan belum selesai. Coba lagi; sistem mencegah aksi ganda.",
                true, MitraRecoveryAction.RETRY
            )
        }
        if (has("UNABLE TO RESOLVE HOST", "FAILED TO CONNECT", "NETWORK IS UNREACHABLE", "NO INTERNET", "NETWORK_OFFLINE")) {
            return MitraFailureUi(
                "NETWORK_OFFLINE", "Tidak ada koneksi internet",
                "Periksa koneksi. Jangan mulai atau menyelesaikan pekerjaan sampai status tersinkron kembali.",
                true, MitraRecoveryAction.RETRY, MitraRecoveryAction.GO_OFFLINE
            )
        }
        if (has("OFFER_EXPIRED")) {
            return MitraFailureUi(
                "OFFER_EXPIRED", "Penawaran sudah berakhir",
                "Penawaran ini sudah kedaluwarsa. Muat ulang untuk melihat penawaran terbaru.",
                false, MitraRecoveryAction.REFRESH_OFFERS
            )
        }
        if (has("OFFER_NOT_ACTIVE", "OFFER_NOT_FOUND", "SLOT_ALREADY_ASSIGNED", "ORDER_NOT_ACCEPTING_ASSIGNMENT")) {
            return MitraFailureUi(
                "OFFER_NO_LONGER_AVAILABLE", "Penawaran sudah tidak tersedia",
                "Order mungkin sudah diterima Mitra lain atau statusnya berubah. Muat ulang penawaran.",
                false, MitraRecoveryAction.REFRESH_OFFERS
            )
        }
        if (has("OFFER_ACCESS_DENIED", "ASSIGNMENT_ACCESS_DENIED")) {
            return MitraFailureUi(
                "ORDER_ACCESS_CHANGED", "Akses order berubah",
                "Order ini tidak lagi terhubung ke akun Anda. Muat ulang daftar order.",
                false, MitraRecoveryAction.REFRESH_ASSIGNMENTS
            )
        }
        if (has("MITRA_OFFER_FEATURE_DISABLED")) {
            return MitraFailureUi(
                "OFFER_UNAVAILABLE", "Penawaran sementara belum tersedia",
                "Fitur penawaran belum aktif untuk akun ini.",
                false, MitraRecoveryAction.REFRESH_OFFERS
            )
        }
        if (has("MITRA_JOB_EXECUTION_FEATURE_DISABLED")) {
            return MitraFailureUi(
                "JOB_EXECUTION_UNAVAILABLE", "Eksekusi pekerjaan sementara belum tersedia",
                "Jangan mulai pekerjaan sebelum fitur ini aktif. Muat ulang status aplikasi.",
                true, MitraRecoveryAction.RETRY
            )
        }
        if (has("PARTNER_NOT_ACTIVE", "PARTNER_NOT_REGISTERED")) {
            return MitraFailureUi(
                "PARTNER_NOT_ACTIVE", "Akun Mitra belum aktif",
                "Selesaikan proses pendaftaran dan verifikasi sebelum menerima order.",
                false, MitraRecoveryAction.OPEN_KYC
            )
        }
        if (has("BASE_KYC_NOT_VERIFIED", "KYC", "DOCUMENT_PENDING", "DOCUMENT_REQUIRED")) {
            return MitraFailureUi(
                "KYC_INCOMPLETE", "Verifikasi Mitra belum selesai",
                "Lengkapi atau tunggu verifikasi dokumen agar layanan dapat diaktifkan.",
                false, MitraRecoveryAction.OPEN_KYC
            )
        }
        if (has("PARTNER_SERVICE_NOT_ELIGIBLE", "PARTNER_SERVICE_NOT_SELECTED")) {
            return MitraFailureUi(
                "SERVICE_NOT_ELIGIBLE", "Layanan belum dapat digunakan",
                "Layanan ini belum aktif atau belum terverifikasi untuk akun Anda.",
                false, MitraRecoveryAction.OPEN_KYC
            )
        }
        if (has("PARTNER_ALREADY_BUSY")) {
            return MitraFailureUi(
                "PARTNER_BUSY", "Masih ada pekerjaan aktif",
                "Selesaikan pekerjaan aktif sebelum menerima order baru.",
                false, MitraRecoveryAction.REFRESH_ASSIGNMENTS
            )
        }
        if (has("PARTNER_PRESENCE_STALE", "ONLINE_LOCATION_REQUIRED", "LOCATION_ACCURACY_INSUFFICIENT", "INVALID_LOCATION_TIMESTAMP")) {
            return MitraFailureUi(
                "LOCATION_NEEDS_REFRESH", "Lokasi perlu diperbarui",
                "Aktifkan GPS dengan akurasi baik lalu perbarui status online.",
                true, MitraRecoveryAction.ENABLE_LOCATION
            )
        }
        if (has("CHECKIN_GPS_REQUIRED", "CHECKIN_ACCURACY_TOO_LOW", "CHECKIN_TOO_FAR", "ORDER_CHECKIN_LOCATION_MISSING")) {
            return MitraFailureUi(
                "CHECKIN_LOCATION_INVALID", "Check-in belum dapat dilakukan",
                "Pastikan Anda berada di lokasi pekerjaan dan GPS cukup akurat, lalu coba lagi.",
                true, MitraRecoveryAction.ENABLE_LOCATION
            )
        }
        if (has("COMPLETION_PROOF_REQUIRED")) {
            return MitraFailureUi(
                "COMPLETION_PROOF_REQUIRED", "Bukti pekerjaan diperlukan",
                "Unggah bukti hasil pekerjaan sebelum menyelesaikan order.",
                false, MitraRecoveryAction.UPLOAD_PROOF
            )
        }
        if (has("OPEN_BLOCKING_ISSUE")) {
            return MitraFailureUi(
                "OPEN_BLOCKING_ISSUE", "Ada kendala yang belum selesai",
                "Selesaikan atau laporkan tindak lanjut kendala sebelum menutup pekerjaan.",
                false, MitraRecoveryAction.OPEN_SUPPORT
            )
        }
        if (has("INVALID_ASSIGNMENT_TRANSITION", "ASSIGNMENT_NOT_FOUND")) {
            return MitraFailureUi(
                "ASSIGNMENT_STATE_CHANGED", "Status pekerjaan sudah berubah",
                "Muat ulang detail pekerjaan sebelum melakukan aksi berikutnya.",
                true, MitraRecoveryAction.REFRESH_ASSIGNMENTS
            )
        }
        if (has("DOCUMENT_ALREADY_UNDER_REVIEW", "VERIFIED_DOCUMENT_STILL_VALID")) {
            return MitraFailureUi(
                "DOCUMENT_ALREADY_PROCESSED", "Dokumen sudah diproses",
                "Tidak perlu mengunggah ulang. Lihat status dokumen terbaru di akun Mitra.",
                false, MitraRecoveryAction.OPEN_KYC
            )
        }
        if (has("PAYOUT_FEATURE_DISABLED", "PAYOUT_UNAVAILABLE")) {
            return MitraFailureUi(
                "PAYOUT_UNAVAILABLE", "Pencairan belum tersedia",
                "Saldo tetap tercatat. Pencairan akan tersedia setelah layanan payout aktif.",
                false, MitraRecoveryAction.NONE
            )
        }
        if (has("PUSH_FEATURE_DISABLED", "PUSH_UNAVAILABLE")) {
            return MitraFailureUi(
                "PUSH_UNAVAILABLE", "Notifikasi push belum tersedia",
                "Tetap pantau Penawaran dan Order dari aplikasi.",
                false, MitraRecoveryAction.REFRESH_OFFERS
            )
        }
        if (has("PERMISSION DENIED FOR TABLE", "PERMISSION_DENIED", "42501", "PGRST", "POSTGREST", "SQLSTATE", "DATABASE ERROR")) {
            return MitraFailureUi(
                "SERVICE_TEMPORARILY_UNAVAILABLE", "Layanan sedang diperbarui",
                "Coba lagi beberapa saat lagi. Bila tetap terjadi, buka Bantuan.",
                true, MitraRecoveryAction.RETRY, MitraRecoveryAction.OPEN_SUPPORT
            )
        }

        return MitraFailureUi(
            "REQUEST_FAILED", fallback,
            "Kami belum dapat menyelesaikan permintaan ini. Coba lagi. Jika tetap terjadi, buka Bantuan.",
            true, MitraRecoveryAction.RETRY, MitraRecoveryAction.OPEN_SUPPORT
        )
    }

    fun message(raw: String?, fallback: String = "Terjadi kendala"): String = map(raw, fallback).message
}

fun mitraSafeError(error: Throwable?, fallback: String = "Terjadi kendala"): String =
    GawoneMitraFailureMapper.message(error?.message, fallback)
''')

for path in src.rglob('*.kt'):
    if path.name in {'SupabaseApi.kt', 'GawoneMitraFailure.kt'}:
        continue
    text = path.read_text()
    original = text
    text = re.sub(
        r'([A-Za-z_][A-Za-z0-9_]*)\.message\s*\?:\s*"([^"]*)"',
        lambda m: f'mitraSafeError({m.group(1)}, "{m.group(2)}")',
        text,
    )
    text = re.sub(
        r'([A-Za-z_][A-Za-z0-9_]*)\.localizedMessage\s*\?:\s*"([^"]*)"',
        lambda m: f'mitraSafeError({m.group(1)}, "{m.group(2)}")',
        text,
    )
    if text != original:
        path.write_text(text)

b = build.read_text()
if 'versionCode = 12' not in b or 'versionName = "1.0.2-stage4j-pilot-rc3"' not in b:
    raise SystemExit('Pilot RC3 version anchor missing')
b = b.replace('versionCode = 12', 'versionCode = 13', 1)
b = b.replace('versionName = "1.0.2-stage4j-pilot-rc3"', 'versionName = "1.0.3-stage4j-reliability-rc4"', 1)
build.write_text(b)

leaks = []
for path in src.rglob('*.kt'):
    if path.name in {'SupabaseApi.kt', 'GawoneMitraFailure.kt'}:
        continue
    text = path.read_text()
    if re.search(r'\.message\s*\?:\s*"', text) or re.search(r'\.localizedMessage\s*\?:\s*"', text):
        leaks.append(str(path))
if leaks:
    raise SystemExit('raw exception UI leak remains: ' + ', '.join(leaks))

if 'versionCode = 13' not in build.read_text():
    raise SystemExit('RC4 version bump failed')
if 'GawoneMitraFailureMapper' not in mapper.read_text():
    raise SystemExit('failure mapper missing')

print('GAWONE Mitra Stage4J Reliability RC4 applied')
