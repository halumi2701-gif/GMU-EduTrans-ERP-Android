package com.garsyanimultiusaha.gmuedutrans.sales

data class V61Handover(
    val id: String,
    val bookingRequestId: String,
    val bookingId: String,
    val institutionName: String,
    val bookingNo: String,
    val handoverStatus: String,
    val bookingStatus: String,
    val requestStatus: String,
    val tripStatus: String,
    val createdAt: String,
    val updatedAt: String
)

data class V61ActionNotice(
    val key: String,
    val kind: String,
    val severity: String,
    val title: String,
    val message: String,
    val targetId: String,
    val isRead: Boolean,
    val occurredAt: String
)

data class V61LearningModule(
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val sortOrder: Int,
    val completedAt: String
)

data class V61Resource(
    val id: String,
    val resourceType: String,
    val title: String,
    val description: String,
    val url: String,
    val shareText: String,
    val sortOrder: Int
)

data class V61Document(
    val id: String,
    val bookingId: String,
    val bookingNo: String,
    val title: String,
    val documentType: String,
    val status: String,
    val fileUrl: String,
    val fileName: String,
    val generatedAt: String
)

data class V61RepeatOpportunity(
    val bookingId: String,
    val bookingNo: String,
    val bookingRequestId: String,
    val customerName: String,
    val picName: String,
    val whatsapp: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val bookingStatus: String,
    val overallScore: Int?,
    val feedback: String,
    val testimonialConsent: Boolean,
    val feedbackSubmittedAt: String
)

data class V61ReleasePolicy(
    val latestVersionCode: Int = BuildConfig.VERSION_CODE,
    val latestVersionName: String = BuildConfig.VERSION_NAME,
    val minSupportedCode: Int = BuildConfig.VERSION_CODE,
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val updateAvailable: Boolean = false,
    val updateRequired: Boolean = false
)

data class V61ConnectedWorkspace(
    val handovers: List<V61Handover> = emptyList(),
    val notifications: List<V61ActionNotice> = emptyList(),
    val learningModules: List<V61LearningModule> = emptyList(),
    val resources: List<V61Resource> = emptyList(),
    val documents: List<V61Document> = emptyList(),
    val repeatOpportunities: List<V61RepeatOpportunity> = emptyList(),
    val releasePolicy: V61ReleasePolicy = V61ReleasePolicy(),
    val integrationErrors: List<String> = emptyList()
)