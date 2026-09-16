package com.garsyanimultiusaha.gmuedutrans.sales

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.ceil

object SalesForecastEngine {
    private const val DEFAULT_AVG_PAX_PER_WIN = 40.0
    private const val PROSPECT_TO_QUALIFIED = 25.0
    private const val QUALIFIED_TO_QUOTATION = 60.0
    private const val QUOTATION_TO_WON = 25.0

    fun build(
        portfolio: SalesPortfolioSummary,
        leads: List<SalesLead>,
        bookings: List<SalesBooking>,
        programs: List<ProgramBreakdown>,
        today: LocalDate = LocalDate.now(ZoneId.of("Asia/Jakarta"))
    ): SalesDashboard {
        val avgWonPax = leads
            .filter { it.stage == "WON" && it.pax > 0 }
            .map { it.pax }
            .average()
            .takeIf { !it.isNaN() && it >= 20.0 }
            ?: DEFAULT_AVG_PAX_PER_WIN

        val funnel = requiredFunnel(portfolio.targetPaidPax, avgWonPax)
        val forecast = forecast(portfolio, leads, today)
        val now = OffsetDateTime.now(ZoneId.of("Asia/Jakarta"))
        val due = leads
            .filter { it.stage !in setOf("WON", "LOST") }
            .filter { lead ->
                val value = lead.nextFollowUpAt
                value.isNotBlank() && runCatching { OffsetDateTime.parse(value).isBefore(now) || OffsetDateTime.parse(value).isEqual(now) }.getOrDefault(false)
            }
            .sortedBy { it.nextFollowUpAt }
        val hot = leads
            .filter { it.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP") || it.probabilityPct >= 55.0 }
            .filter { it.stage !in setOf("WON", "LOST") }
            .sortedByDescending { it.probabilityPct }

        return SalesDashboard(
            portfolio = portfolio,
            leads = leads,
            bookings = bookings,
            programs = programs,
            funnel = funnel,
            forecast = forecast,
            followUpsDue = due,
            hotLeads = hot
        )
    }

    fun requiredFunnel(targetPaidPax: Int, averagePaxPerWon: Double): FunnelRequirement {
        val safeAvg = averagePaxPerWon.coerceAtLeast(20.0)
        val wins = ceil(targetPaidPax / safeAvg).toInt().coerceAtLeast(1)
        val quotations = ceil(wins / (QUOTATION_TO_WON / 100.0)).toInt()
        val qualified = ceil(quotations / (QUALIFIED_TO_QUOTATION / 100.0)).toInt()
        val prospects = ceil(qualified / (PROSPECT_TO_QUALIFIED / 100.0)).toInt()
        return FunnelRequirement(
            targetPaidPax = targetPaidPax,
            assumedAveragePaxPerWon = safeAvg,
            requiredWonBookings = wins,
            requiredQuotations = quotations,
            requiredQualifiedLeads = qualified,
            requiredProspects = prospects,
            prospectToQualifiedPct = PROSPECT_TO_QUALIFIED,
            qualifiedToQuotationPct = QUALIFIED_TO_QUOTATION,
            quotationToWonPct = QUOTATION_TO_WON
        )
    }

    fun forecast(
        portfolio: SalesPortfolioSummary,
        leads: List<SalesLead>,
        today: LocalDate
    ): SalesForecast {
        val target = portfolio.targetPaidPax.coerceAtLeast(1)
        val paid = portfolio.paidPax.coerceAtLeast(0)
        val remaining = (target - paid).coerceAtLeast(0)
        val open = leads.filter { it.stage !in setOf("WON", "LOST") }
        val rawPipeline = open.sumOf { it.pax.coerceAtLeast(0) }
        val weightedPipeline = open.sumOf { it.pax.coerceAtLeast(0) * (it.probabilityPct.coerceIn(0.0, 100.0) / 100.0) }
        val weightedForecast = paid + weightedPipeline
        val elapsed = today.dayOfMonth.coerceAtLeast(1)
        val daysInMonth = today.lengthOfMonth()
        val remainingDays = (daysInMonth - today.dayOfMonth + 1).coerceAtLeast(0)
        val paceProjection = if (elapsed > 0) paid.toDouble() / elapsed * daysInMonth else paid.toDouble()
        val requiredPerDay = if (remainingDays > 0) remaining.toDouble() / remainingDays else remaining.toDouble()
        val coverage = if (remaining > 0) rawPipeline.toDouble() / remaining else if (paid >= target) 999.0 else 0.0

        val status = when {
            paid >= target -> ForecastStatus.TARGET_REACHED
            paceProjection >= target && weightedForecast >= target -> ForecastStatus.ON_TRACK
            weightedForecast >= target || rawPipeline >= remaining * 1.5 -> ForecastStatus.PIPELINE_CAN_COVER
            rawPipeline >= remaining || weightedForecast >= target * 0.8 -> ForecastStatus.AT_RISK
            else -> ForecastStatus.PIPELINE_INSUFFICIENT
        }

        val message = when (status) {
            ForecastStatus.TARGET_REACHED -> "Target 400 pax sudah tercapai. Fokus berikutnya repeat order dan stretch target."
            ForecastStatus.ON_TRACK -> "Kecepatan paid pax dan weighted pipeline saat ini berada pada jalur target."
            ForecastStatus.PIPELINE_CAN_COVER -> "Pipeline saat ini secara kapasitas dapat menutup sisa target, tetapi tetap perlu konversi dan pembayaran."
            ForecastStatus.AT_RISK -> "Target masih dapat dikejar, tetapi pipeline atau pace belum memberi buffer yang aman. Prioritaskan HOT lead dan quotation."
            ForecastStatus.PIPELINE_INSUFFICIENT -> "Pipeline saat ini belum cukup untuk menutup target. Tambah prospek baru dan percepat lead menuju quotation."
        }

        return SalesForecast(
            paidPax = paid,
            targetPax = target,
            remainingPax = remaining,
            rawOpenPipelinePax = rawPipeline,
            weightedOpenPipelinePax = weightedPipeline,
            weightedForecastPax = weightedForecast,
            paceProjectionPax = paceProjection,
            remainingDaysInMonth = remainingDays,
            requiredPaidPaxPerDay = requiredPerDay,
            pipelineCoverage = coverage,
            status = status,
            message = message
        )
    }
}
