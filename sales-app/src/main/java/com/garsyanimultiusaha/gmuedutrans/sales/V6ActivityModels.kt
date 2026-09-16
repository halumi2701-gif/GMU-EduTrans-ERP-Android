package com.garsyanimultiusaha.gmuedutrans.sales

data class V6Attendance(
    val attendanceDate: String = "",
    val status: String = "Belum Absen",
    val checkIn: String = "",
    val checkOut: String = "",
    val notes: String = ""
)

data class V6VisitRecord(
    val id: String,
    val bookingRequestId: String,
    val institutionName: String,
    val picName: String,
    val visitStatus: String,
    val checkInAt: String,
    val checkOutAt: String,
    val outcome: String,
    val notes: String,
    val nextFollowUpAt: String
)

data class V6DailyReport(
    val id: String,
    val reportDate: String,
    val attendanceStatus: String,
    val checkIn: String,
    val checkOut: String,
    val newLeads: Int,
    val visitsCompleted: Int,
    val followupActivities: Int,
    val quotationsCreated: Int,
    val quotationsSent: Int,
    val wonLeads: Int,
    val obstacles: String,
    val tomorrowPlan: String,
    val notes: String,
    val submittedAt: String
)

data class V6FieldWorkspace(
    val attendance: V6Attendance? = null,
    val visits: List<V6VisitRecord> = emptyList(),
    val reports: List<V6DailyReport> = emptyList()
)
