package com.garsyanimultiusaha.gmuedutrans.erp

/**
 * Navigation metadata for ERP v20.
 *
 * Keep role authorization in [RoleAccess]. This catalog only defines information
 * architecture: where a page belongs, how it is labelled, and which primary tab
 * represents it. UI surfaces should consume this catalog instead of maintaining
 * their own duplicate page lists.
 */
enum class WorkspaceGroup(val label: String) {
    SALES("Sales & Booking"),
    OPERATIONS("Operations"),
    FINANCE("Finance & Control"),
    PEOPLE("People & Governance"),
    SYSTEM("System")
}

data class WorkspaceDestination(
    val page: AppPage,
    val label: String,
    val description: String,
    val group: WorkspaceGroup,
    val primaryTab: MainTab = MainTab.MORE
)

object ErpNavigation {
    val destinations: List<WorkspaceDestination> = listOf(
        WorkspaceDestination(AppPage.DASHBOARD, "Dashboard", "Ringkasan kerja dan item yang perlu tindakan", WorkspaceGroup.SYSTEM, MainTab.HOME),
        WorkspaceDestination(AppPage.BOOKINGS, "Booking", "Pipeline, order, pax dan status perjalanan", WorkspaceGroup.SALES, MainTab.BOOKING),
        WorkspaceDestination(AppPage.BOOKING_REQUESTS, "Pengajuan Website", "Permintaan booking yang masuk dari kanal online", WorkspaceGroup.SALES),
        WorkspaceDestination(AppPage.QUOTATIONS, "Quotation & Pricing", "Penawaran, kalkulasi harga dan persetujuan", WorkspaceGroup.SALES),
        WorkspaceDestination(AppPage.PACKAGE_MASTER, "Package Master", "Master program, fasilitas dan komponen paket", WorkspaceGroup.SALES),
        WorkspaceDestination(AppPage.PRICING_MASTER, "Pricing Master", "Aturan harga, margin dan komponen biaya", WorkspaceGroup.SALES),
        WorkspaceDestination(AppPage.CUSTOMERS, "Customer", "Sekolah, instansi, komunitas dan riwayat order", WorkspaceGroup.SALES, MainTab.BOOKING),
        WorkspaceDestination(AppPage.OPERATIONS, "Trip Control", "Kesiapan trip, crew, rundown dan operation sheet", WorkspaceGroup.OPERATIONS, MainTab.TRIP),
        WorkspaceDestination(AppPage.VENDORS, "Vendor & PO", "Vendor, purchase order dan kebutuhan operasional", WorkspaceGroup.OPERATIONS),
        WorkspaceDestination(AppPage.TRIP_FOLDER, "Trip Folder", "Dokumen perjalanan dalam satu folder kerja", WorkspaceGroup.OPERATIONS, MainTab.TRIP),
        WorkspaceDestination(AppPage.SOP, "SOP & Deadline", "SOP, checklist dan tenggat eksekusi", WorkspaceGroup.OPERATIONS),
        WorkspaceDestination(AppPage.CLOSING, "Trip Closing", "Realisasi biaya, laporan dan penutupan trip", WorkspaceGroup.OPERATIONS),
        WorkspaceDestination(AppPage.FINANCE, "Finance", "Omzet, piutang, biaya, kas, margin dan profit", WorkspaceGroup.FINANCE, MainTab.FINANCE),
        WorkspaceDestination(AppPage.PAYMENT_GATEWAY, "Payment Gateway", "Status dan kontrol pembayaran customer", WorkspaceGroup.FINANCE),
        WorkspaceDestination(AppPage.PLANNING, "Planning & Control", "RAB, forecast dan kontrol rencana vs aktual", WorkspaceGroup.FINANCE),
        WorkspaceDestination(AppPage.REPORTS, "Reports", "Laporan manajemen, operasional dan keuangan", WorkspaceGroup.FINANCE),
        WorkspaceDestination(AppPage.WORKFLOW, "Approval", "Approval, escalation dan jejak keputusan", WorkspaceGroup.PEOPLE),
        WorkspaceDestination(AppPage.TEAM_HR, "Team & HR", "Tim, kontrak, KPI dan administrasi SDM", WorkspaceGroup.PEOPLE),
        WorkspaceDestination(AppPage.AUDIT, "Audit Trail", "Jejak perubahan dan kontrol akuntabilitas", WorkspaceGroup.PEOPLE),
        WorkspaceDestination(AppPage.USERS, "User & Role", "Akun, role dan hak akses", WorkspaceGroup.SYSTEM),
        WorkspaceDestination(AppPage.PROFILE, "Workspace", "Profil pengguna dan seluruh tools yang diizinkan", WorkspaceGroup.SYSTEM, MainTab.MORE)
    )

    private val byPage = destinations.associateBy { it.page }

    fun destination(page: AppPage): WorkspaceDestination =
        byPage[page] ?: WorkspaceDestination(page, page.name, "", WorkspaceGroup.SYSTEM)

    fun mainTabFor(page: AppPage): MainTab = destination(page).primaryTab

    fun visibleDestinations(role: String): List<WorkspaceDestination> {
        val allowed = RoleAccess.pages(role)
        return destinations.filter { it.page in allowed }
    }

    fun visibleGroups(role: String): Map<WorkspaceGroup, List<WorkspaceDestination>> =
        visibleDestinations(role)
            .filter { it.page !in setOf(AppPage.DASHBOARD, AppPage.PROFILE) }
            .groupBy { it.group }
}
