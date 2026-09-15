package com.garsyanimultiusaha.gmuedutrans.erp

/**
 * Business-domain boundaries for ERP v21.1 production readiness.
 *
 * Physical UI ownership:
 * - DashboardCoreScreens.kt -> executive/operational snapshot
 * - SalesCoreScreens.kt -> booking + customer workflow
 * - FinanceCoreScreens.kt -> financial health + finance entry workflow
 * - PackageMasterScreens.kt -> package catalog lifecycle
 * - PricingMasterScreens.kt -> cost templates + pricing policy/readiness
 * - PaymentGatewayScreens.kt -> payment channels + payment orders
 * - CommerceSharedUi.kt -> commerce-only UI primitives
 * - SharedCoreUi.kt -> cross-domain primitives only
 * - ErpPageHost.kt -> page routing only, never business logic
 * - MainViewModel.kt -> session/action coordinator, never presentation analytics
 * - DashboardDomainQueries.kt -> read-only dashboard/finance projections
 *
 * UI authorization remains enforced by [RoleAccess]. This layer owns reusable
 * role policy and data-source ownership so ViewModels/screens do not repeat raw
 * role strings and table lists throughout the codebase.
 */
object ErpRolePolicy {
    private val bookingEditors = setOf("Owner", "Manager", "Admin", "Sales")
    private val bookingIntake = setOf("Owner", "Manager", "Admin", "Sales")
    private val quotationDraftManagers = setOf("Owner", "Manager", "Admin", "Sales")
    private val commercialApprovers = setOf("Owner", "Manager")
    private val operations = setOf("Owner", "Manager", "Admin", "Operation", "TL")
    private val vendorManagers = setOf("Owner", "Manager", "Admin", "Operation")
    private val approvalReaders = setOf("Owner", "Manager", "Admin", "Finance", "Operation")
    private val auditReaders = setOf("Owner", "Manager", "Admin", "Finance", "Operation")
    private val peopleManagers = setOf("Owner", "Manager", "Admin")
    private val packageManagers = setOf("Owner", "Manager", "Admin")

    private fun accessRole(role: String): String = when {
        role == ErpRoles.OWNER || ErpRoles.isDirector(role) -> "Owner"
        ErpRoles.isManagerEduTrans(role) -> "Manager"
        else -> role
    }

    fun canEditBookings(role: String) = accessRole(role) in bookingEditors
    fun canHandleBookingIntake(role: String) = accessRole(role) in bookingIntake
    fun canManageQuotationDrafts(role: String) = accessRole(role) in quotationDraftManagers
    fun canApproveCommercial(role: String) = accessRole(role) in commercialApprovers
    fun canOperateTrips(role: String) = accessRole(role) in operations
    fun canManageVendors(role: String) = accessRole(role) in vendorManagers
    fun canReadApprovals(role: String) = accessRole(role) in approvalReaders
    fun canReadAudit(role: String) = accessRole(role) in auditReaders
    fun canManagePeople(role: String) = accessRole(role) in peopleManagers
    fun canManagePackages(role: String) = accessRole(role) in packageManagers
    fun isOwner(role: String) = accessRole(role) == "Owner"
}

data class ErpTableSource(
    val table: String,
    val order: String? = null,
    val enabledFor: (String) -> Boolean
)

object ErpDataRegistry {
    private val sources = listOf(
        ErpTableSource("payments", "payment_date.desc") { FinancialAccess.canView(it) },
        ErpTableSource("trip_costs", "created_at.desc") { FinancialAccess.canView(it) },

        ErpTableSource("trips", "updated_at.desc", ErpRolePolicy::canOperateTrips),
        ErpTableSource("operation_sheets", "updated_at.desc", ErpRolePolicy::canOperateTrips),
        ErpTableSource("manifests", enabledFor = ErpRolePolicy::canOperateTrips),
        ErpTableSource("attendance", enabledFor = ErpRolePolicy::canOperateTrips),
        ErpTableSource("rundown_items", enabledFor = ErpRolePolicy::canOperateTrips),
        ErpTableSource("documents", "generated_at.desc", ErpRolePolicy::canOperateTrips),
        ErpTableSource("trip_reports", "created_at.desc", ErpRolePolicy::canOperateTrips),
        ErpTableSource("evaluations", "created_at.desc", ErpRolePolicy::canOperateTrips),
        ErpTableSource("sop_deadlines", enabledFor = ErpRolePolicy::canOperateTrips),

        ErpTableSource("vendors", "created_at.desc", ErpRolePolicy::canManageVendors),
        ErpTableSource("vendor_pos", "created_at.desc", ErpRolePolicy::canManageVendors),
        ErpTableSource("approvals", "requested_at.desc", ErpRolePolicy::canReadApprovals),
        ErpTableSource("trip_closings", "closed_at.desc") { FinancialAccess.canView(it) },
        ErpTableSource("audit_logs", "created_at.desc", ErpRolePolicy::canReadAudit),
        ErpTableSource("profiles", "created_at.desc", ErpRolePolicy::canManageVendors),

        ErpTableSource("programs", "sort_order.asc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_attendance", "attendance_date.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_assignments", "created_at.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_kpis", "created_at.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_reviews", "reviewed_at.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_leave", "created_at.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_warnings", "created_at.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_training", "training_date.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_contracts", "created_at.desc", ErpRolePolicy::canManagePeople),
        ErpTableSource("staff_offboarding", "created_at.desc", ErpRolePolicy::canManagePeople)
    )

    fun tablesForRole(role: String): List<Pair<String, String?>> =
        sources.asSequence()
            .filter { it.enabledFor(role) }
            .map { it.table to it.order }
            .distinctBy { it.first }
            .toList()
}
