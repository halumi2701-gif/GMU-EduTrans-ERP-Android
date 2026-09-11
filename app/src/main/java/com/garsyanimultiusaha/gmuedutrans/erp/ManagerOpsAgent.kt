package com.garsyanimultiusaha.gmuedutrans.erp

/**
 * Foundation for the GMU EduTrans Ops Agent used by the Manager EduTrans role.
 * The UI/LLM layer can consume this policy without giving the agent unrestricted authority.
 */
object ManagerOpsAgent {
    const val displayName = "GMU EduTrans Ops Agent"

    val quickActions = listOf(
        "Siapkan Trip",
        "Buat Rundown",
        "Cek Kesiapan",
        "Susun Crew",
        "Cek Vendor",
        "Analisis RAB",
        "Buat Operation Sheet",
        "Buat Laporan"
    )

    fun isAvailableFor(role: String): Boolean =
        ErpRoles.isManagerEduTrans(role) || ErpRoles.isDirector(role) || role == ErpRoles.OWNER

    enum class ActionAuthority {
        AUTO,
        MANAGER_CONFIRMATION,
        DIRECTOR_APPROVAL
    }

    fun authorityFor(action: String, amountIdr: Long = 0L): ActionAuthority {
        if (amountIdr > 0 && ManagerEduTransPolicy.requiresDirectorApproval(amountIdr)) {
            return ActionAuthority.DIRECTOR_APPROVAL
        }

        val normalized = action.lowercase()
        return when {
            listOf("refund", "closing", "hapus piutang", "ubah harga", "rekening", "jurnal").any(normalized::contains) ->
                ActionAuthority.DIRECTOR_APPROVAL
            listOf("assign", "kirim", "approve", "ubah", "final", "konfirmasi vendor").any(normalized::contains) ->
                ActionAuthority.MANAGER_CONFIRMATION
            else -> ActionAuthority.AUTO
        }
    }

    data class Readiness(
        val score: Int,
        val missing: List<String>
    )

    fun calculateReadiness(
        hasRundown: Boolean,
        hasManifest: Boolean,
        hasTransport: Boolean,
        hasVendorConfirmation: Boolean,
        hasCrew: Boolean,
        hasRab: Boolean,
        hasChecklist: Boolean
    ): Readiness {
        val checks = listOf(
            "Rundown" to hasRundown,
            "Manifest" to hasManifest,
            "Transport" to hasTransport,
            "Vendor" to hasVendorConfirmation,
            "Crew" to hasCrew,
            "RAB" to hasRab,
            "Checklist" to hasChecklist
        )
        val completed = checks.count { it.second }
        val score = ((completed.toDouble() / checks.size) * 100).toInt()
        return Readiness(score = score, missing = checks.filterNot { it.second }.map { it.first })
    }
}
