package com.garsyanimultiusaha.gmuedutrans.sales

data class SalesProfile(
    val id: String,
    val fullName: String,
    val role: String,
    val active: Boolean
)

data class SalesSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val profile: SalesProfile
)

data class SalesLead(
    val id: String,
    val bookingCode: String,
    val institutionName: String,
    val picName: String,
    val whatsapp: String,
    val city: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val source: String,
    val stage: String,
    val probabilityPct: Double,
    val nextFollowUpAt: String,
    val lastContactAt: String,
    val createdAt: String,
    val convertedBookingId: String
)

data class SalesBooking(
    val id: String,
    val bookingNo: String,
    val customerName: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val status: String
)

data class SalesPackage(
    val id: String,
    val programId: String,
    val name: String,
    val description: String,
    val pricePerPax: Double,
    val minPax: Int,
    val facilities: List<String>,
    val priceNote: String
)

data class SalesProgram(
    val id: String,
    val slug: String,
    val name: String,
    val category: String,
    val description: String,
    val minPax: Int,
    val marketingStartPrice: Double?,
    val marketingPriceNote: String,
    val packages: List<SalesPackage>
)

data class SalesKitTemplate(
    val id: String,
    val category: String,
    val title: String,
    val body: String
)

data class NewLeadInput(
    val customerType: String = "Sekolah",
    val institutionName: String,
    val picName: String,
    val whatsapp: String,
    val city: String,
    val programId: String?,
    val customProgram: String,
    val tripDate: String,
    val pax: Int,
    val budgetPerPax: Double?,
    val notes: String
)

data class SalesPortfolioSummary(
    val targetName: String = "Target Sales GMU EduTrans",
    val paidBookings: Int = 0,
    val paidPax: Int = 0,
    val bepPaidPax: Int = 60,
    val productivePaidPax: Int = 200,
    val targetPaidPax: Int = 400,
    val stretchPaidPax: Int = 600,
    val outstandingPaidPax: Int = 800,
    val achievementPct: Double = 0.0,
    val salesRetainer: Double = 600_000.0,
    val salesFeePerPaidPax: Double = 2_500.0,
    val variableSalesFee: Double = 0.0,
    val targetBonusEarned: Double = 0.0,
    val modeledSalesIncome: Double = 600_000.0,
    val achievementLevel: String = "DI_BAWAH_BEP"
)

data class ProgramBreakdown(
    val programName: String,
    val paidBookings: Int,
    val paidPax: Int
)

data class FunnelRequirement(
    val targetPaidPax: Int,
    val assumedAveragePaxPerWon: Double,
    val requiredWonBookings: Int,
    val requiredQuotations: Int,
    val requiredQualifiedLeads: Int,
    val requiredProspects: Int,
    val prospectToQualifiedPct: Double,
    val qualifiedToQuotationPct: Double,
    val quotationToWonPct: Double
)

data class SalesForecast(
    val paidPax: Int,
    val targetPax: Int,
    val remainingPax: Int,
    val rawOpenPipelinePax: Int,
    val weightedOpenPipelinePax: Double,
    val weightedForecastPax: Double,
    val paceProjectionPax: Double,
    val remainingDaysInMonth: Int,
    val requiredPaidPaxPerDay: Double,
    val pipelineCoverage: Double,
    val status: ForecastStatus,
    val message: String
)

enum class ForecastStatus {
    TARGET_REACHED,
    ON_TRACK,
    PIPELINE_CAN_COVER,
    AT_RISK,
    PIPELINE_INSUFFICIENT
}

data class SalesDashboard(
    val portfolio: SalesPortfolioSummary = SalesPortfolioSummary(),
    val leads: List<SalesLead> = emptyList(),
    val bookings: List<SalesBooking> = emptyList(),
    val programs: List<ProgramBreakdown> = emptyList(),
    val catalog: List<SalesProgram> = emptyList(),
    val funnel: FunnelRequirement = FunnelRequirement(400, 40.0, 10, 40, 67, 268, 25.0, 60.0, 25.0),
    val forecast: SalesForecast = SalesForecast(0, 400, 400, 0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, ForecastStatus.PIPELINE_INSUFFICIENT, "Pipeline belum cukup untuk menutup target."),
    val followUpsDue: List<SalesLead> = emptyList(),
    val hotLeads: List<SalesLead> = emptyList()
)

sealed interface SalesAppState {
    data object Splash : SalesAppState
    data object LoggedOut : SalesAppState
    data object Loading : SalesAppState
    data class LoggedIn(val session: SalesSession) : SalesAppState
    data class Error(val message: String) : SalesAppState
}

enum class SalesPage { DASHBOARD, LEADS, FOLLOW_UP, MARKETING_KIT, FUNNEL, BOOKINGS, EARNINGS, PROFILE }
