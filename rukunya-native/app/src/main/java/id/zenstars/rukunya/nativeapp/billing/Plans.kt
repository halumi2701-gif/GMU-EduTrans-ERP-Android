package id.zenstars.rukunya.nativeapp.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Feature {
    CORE_WARGA,
    CORE_IURAN,
    CORE_KAS,
    CORE_SURAT,
    CORE_INFO,
    LOCAL_BACKUP,
    UNLIMITED_KK,
    CUSTOM_IURAN,
    ADVANCED_REPORT,
    EXPORT_CSV,
    EXPORT_PDF,
    MULTI_ADMIN,
    CLOUD_SYNC,
    RESIDENT_ACCESS,
    FULL_HISTORY,
    WHATSAPP_REMINDER,
    LETTER_APPROVAL,
    RW_DASHBOARD,
    AUTO_REMINDER
}

data class PlanSpec(
    val plan: Plan,
    val title: String,
    val priceLabel: String,
    val description: String,
    val maxHouseholds: Int?,
    val maxAdmins: Int,
    val features: Set<Feature>
) {
    fun has(feature: Feature): Boolean = feature in features
}

enum class Plan { FREE, BASIC, PLUS, PRO }

object Plans {
    val free: PlanSpec = PlanSpec(
        plan = Plan.FREE,
        title = "FREE",
        priceLabel = "Rp0",
        description = "Untuk mencoba dan RT kecil",
        maxHouseholds = 30,
        maxAdmins = 1,
        features = setOf(
            Feature.CORE_WARGA,
            Feature.CORE_IURAN,
            Feature.CORE_KAS,
            Feature.CORE_SURAT,
            Feature.CORE_INFO,
            Feature.LOCAL_BACKUP
        )
    )

    val basic: PlanSpec = PlanSpec(
        plan = Plan.BASIC,
        title = "Basic",
        priceLabel = "Rp29.000/bulan",
        description = "Administrasi RT aktif tanpa batas KK",
        maxHouseholds = null,
        maxAdmins = 1,
        features = free.features + setOf(
            Feature.UNLIMITED_KK,
            Feature.CUSTOM_IURAN,
            Feature.ADVANCED_REPORT,
            Feature.EXPORT_CSV,
            Feature.EXPORT_PDF,
            Feature.FULL_HISTORY
        )
    )

    val plus: PlanSpec = PlanSpec(
        plan = Plan.PLUS,
        title = "Plus",
        priceLabel = "Rp59.000/bulan",
        description = "Untuk pengurus yang bekerja bersama",
        maxHouseholds = null,
        maxAdmins = 3,
        features = basic.features + setOf(
            Feature.MULTI_ADMIN,
            Feature.CLOUD_SYNC,
            Feature.RESIDENT_ACCESS
        )
    )

    val pro: PlanSpec = PlanSpec(
        plan = Plan.PRO,
        title = "Pro",
        priceLabel = "Rp99.000/bulan",
        description = "Otomasi dan operasional RT/RW lengkap",
        maxHouseholds = null,
        maxAdmins = 10,
        features = plus.features + setOf(
            Feature.WHATSAPP_REMINDER,
            Feature.LETTER_APPROVAL,
            Feature.RW_DASHBOARD,
            Feature.AUTO_REMINDER
        )
    )

    val all: List<PlanSpec> = listOf(free, basic, plus, pro)

    fun get(plan: Plan): PlanSpec = when (plan) {
        Plan.FREE -> free
        Plan.BASIC -> basic
        Plan.PLUS -> plus
        Plan.PRO -> pro
    }
}

class PlanStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rukunya_plan", Context.MODE_PRIVATE)
    private val _plan: MutableStateFlow<Plan> = MutableStateFlow(
        runCatching { Plan.valueOf(prefs.getString("active_plan", Plan.FREE.name) ?: Plan.FREE.name) }
            .getOrDefault(Plan.FREE)
    )
    val plan: StateFlow<Plan> = _plan

    fun spec(): PlanSpec = Plans.get(_plan.value)

    /**
     * Entry point for the future license/billing verifier. UI does not expose arbitrary plan switching.
     */
    fun applyVerifiedPlan(plan: Plan) {
        prefs.edit().putString("active_plan", plan.name).apply()
        _plan.value = plan
    }
}
