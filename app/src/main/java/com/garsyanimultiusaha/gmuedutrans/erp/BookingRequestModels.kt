package com.garsyanimultiusaha.gmuedutrans.erp

data class BookingRequestItem(
    val id: String,
    val bookingCode: String,
    val status: String,
    val institutionName: String,
    val picName: String,
    val whatsapp: String,
    val email: String,
    val city: String,
    val programName: String,
    val customProgram: String,
    val tripDate: String,
    val pax: Int,
    val companionPax: Int,
    val participantGroup: String,
    val meetingPoint: String,
    val source: String,
    val createdAt: String,
    val updatedAt: String,
    val convertedBookingId: String
)


data class CustomerPortalCredential(
    val requestId: String,
    val bookingCode: String,
    val accessToken: String,
    val institutionName: String,
    val picName: String,
    val status: String
)
