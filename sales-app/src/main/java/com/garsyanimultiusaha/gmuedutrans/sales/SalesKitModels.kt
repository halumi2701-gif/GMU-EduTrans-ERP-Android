package com.garsyanimultiusaha.gmuedutrans.sales

data class SalesKitProgram(
    val id: String,
    val name: String,
    val category: String,
    val shortDescription: String,
    val marketingStartPrice: Double?,
    val marketingPriceNote: String,
    val minPax: Int,
    val packages: List<SalesKitPackage>
)

data class SalesKitPackage(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val publicPricePerPax: Double,
    val minPax: Int,
    val facilities: List<String>,
    val b2bNetPricePerPax: Double?
)

data class SalesKitScript(
    val title: String,
    val purpose: String,
    val text: String
)
