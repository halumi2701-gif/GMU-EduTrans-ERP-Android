package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class SalesKitApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun loadCatalog(session: SalesSession): List<SalesKitProgram> = withContext(Dispatchers.IO) {
        val programRows = JSONArray(
            request(
                "/rest/v1/programs?select=id,name,category,short_description,min_pax,marketing_start_price,marketing_price_note&is_active=eq.true&order=sort_order.asc",
                session.accessToken
            )
        )

        val packageRows = JSONArray(
            request(
                "/rest/v1/program_packages?select=id,program_id,package_code,name,description,price_per_pax,min_pax,facilities&is_active=eq.true&status=eq.ACTIVE&order=sort_order.asc",
                session.accessToken
            )
        )

        val channelRows = JSONArray(
            request(
                "/rest/v1/program_package_channel_prices?select=package_code,net_price_per_pax,effective_from,effective_until&channel=eq.B2B_MOU&is_active=eq.true&order=effective_from.desc",
                session.accessToken
            )
        )

        val today = LocalDate.now()
        val b2bByCode = mutableMapOf<String, Double>()
        for (i in 0 until channelRows.length()) {
            val row = channelRows.getJSONObject(i)
            val code = row.optString("package_code")
            if (code.isBlank() || b2bByCode.containsKey(code)) continue
            val from = row.optString("effective_from").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            val until = row.optString("effective_until").takeIf { it.isNotBlank() && it != "null" }?.let(LocalDate::parse)
            val activeNow = (from == null || !from.isAfter(today)) && (until == null || !until.isBefore(today))
            if (activeNow && !row.isNull("net_price_per_pax")) {
                b2bByCode[code] = row.optDouble("net_price_per_pax")
            }
        }

        val packagesByProgram = mutableMapOf<String, MutableList<SalesKitPackage>>()
        for (i in 0 until packageRows.length()) {
            val row = packageRows.getJSONObject(i)
            val programId = row.optString("program_id")
            if (programId.isBlank()) continue
            val code = row.optString("package_code")
            val facilities = mutableListOf<String>()
            val facilityArray = row.optJSONArray("facilities") ?: JSONArray()
            for (f in 0 until facilityArray.length()) {
                facilityArray.optString(f).takeIf { it.isNotBlank() }?.let(facilities::add)
            }
            packagesByProgram.getOrPut(programId) { mutableListOf() }.add(
                SalesKitPackage(
                    id = row.optString("id"),
                    code = code,
                    name = row.optString("name", "Paket GMU EduTrans"),
                    description = row.optString("description", ""),
                    publicPricePerPax = row.optDouble("price_per_pax", 0.0),
                    minPax = row.optInt("min_pax", 1),
                    facilities = facilities,
                    b2bNetPricePerPax = b2bByCode[code]
                )
            )
        }

        buildList {
            for (i in 0 until programRows.length()) {
                val row = programRows.getJSONObject(i)
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(
                    SalesKitProgram(
                        id = id,
                        name = row.optString("name", "Program GMU EduTrans"),
                        category = row.optString("category", "Program Edukasi"),
                        shortDescription = row.optString("short_description", ""),
                        marketingStartPrice = nullableDouble(row, "marketing_start_price"),
                        marketingPriceNote = row.optString("marketing_price_note", ""),
                        minPax = row.optInt("min_pax", 1),
                        packages = packagesByProgram[id].orEmpty()
                    )
                )
            }
        }
    }

    private fun nullableDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        return value.takeUnless { it.isNaN() }
    }

    private fun request(path: String, accessToken: String): String {
        val connection = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val obj = JSONObject(text)
                obj.optString("message").ifBlank { obj.optString("hint") }.ifBlank { obj.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "Marketing Kit gagal dimuat (HTTP $code)." })
        }
        return text.ifBlank { "[]" }
    }
}
