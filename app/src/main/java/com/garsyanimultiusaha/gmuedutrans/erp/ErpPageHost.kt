package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.runtime.Composable

/**
 * Single page-router for ERP v20.
 *
 * The application shell must only care about navigation chrome. Screen ownership
 * lives here so each business domain can be moved into its own package/file later
 * without changing the shell again.
 */
@Composable
fun ErpPageHost(
    page: AppPage,
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    when (page) {
        AppPage.DASHBOARD -> DashboardScreen(vm, session)

        // Sales & Booking
        AppPage.BOOKINGS -> BookingScreen(vm, session, onNotice)
        AppPage.BOOKING_REQUESTS -> BookingRequestScreen(vm, session, onNotice)
        AppPage.QUOTATIONS -> QuotationPricingScreen(vm, session, onNotice)
        AppPage.PACKAGE_MASTER -> PackageMasterHubScreen(vm, session, onNotice)
        AppPage.PRICING_MASTER -> PricingMasterScreen(vm, session, onNotice)
        AppPage.CUSTOMERS -> CustomerScreen(vm, session, onNotice)

        // Operations
        AppPage.OPERATIONS -> OperationsScreen(vm, session, onNotice)
        AppPage.VENDORS -> VendorsScreen(vm, session, onNotice)
        AppPage.TRIP_FOLDER -> TripFolderScreen(vm, session, onNotice)
        AppPage.SOP -> SopScreen(vm)
        AppPage.CLOSING -> ClosingScreen(vm, session, onNotice)

        // Finance & Control
        AppPage.FINANCE -> FinanceScreen(vm, session, onNotice)
        AppPage.PAYMENT_GATEWAY -> PaymentGatewayScreen(vm, session, onNotice)
        AppPage.PLANNING -> PlanningScreen(vm, session, onNotice)
        AppPage.REPORTS -> ReportsScreen(vm, session, onNotice)

        // People & Governance
        AppPage.WORKFLOW -> WorkflowScreen(vm, session, onNotice)
        AppPage.TEAM_HR -> HrScreen(vm, session, onNotice)
        AppPage.AUDIT -> AuditScreen(vm)

        // System
        AppPage.USERS -> UsersAndSalesApplicationsScreen(vm, session, onNotice)
        AppPage.PROFILE -> MoreProfileScreen(vm, session)
    }
}
