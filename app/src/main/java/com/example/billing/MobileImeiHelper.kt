package com.example.billing

object MobileImeiHelper {

    private val MOBILE_KEYWORDS = listOf(
        "mobile", "phone", "smartphone", "handset", "iphone",
        "samsung", "galaxy", "redmi", "xiaomi", "realme",
        "vivo", "oppo", "oneplus", "poco", "motorola",
        "moto ", "nokia", "iqoo", "nothing", "pixel",
        "tecno", "infinix", "lava", "micromax", "itel",
        "honor", "5g", "4g", "imei", "tablet", "ipad"
    )

    fun isLikelyMobilePhone(name: String, hsnCode: String = ""): Boolean {
        val cleanHsn = hsnCode.trim()
        if (cleanHsn.startsWith("8517")) return true
        val lowerName = " ${name.trim().lowercase()} "
        return MOBILE_KEYWORDS.any { keyword -> lowerName.contains(keyword) }
    }

    fun normalizeImeiInput(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9/,\\-\\s]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .take(80)
    }

    fun combineImeiNumbers(imei1: String, imei2: String): String {
        val clean1 = normalizeImeiInput(imei1).trim()
        val clean2 = normalizeImeiInput(imei2).trim()
        return listOf(clean1, clean2)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
            .take(80)
    }

    fun splitImeiNumbers(combined: String): Pair<String, String> {
        val trimmed = combined.trim()
        if (trimmed.isEmpty()) return "" to ""
        val parts = if (trimmed.contains("/")) {
            trimmed.split("/", limit = 2)
        } else if (trimmed.contains(",")) {
            trimmed.split(",", limit = 2)
        } else {
            listOf(trimmed)
        }
        val first = parts.getOrNull(0)?.trim().orEmpty()
        val second = parts.getOrNull(1)?.trim().orEmpty()
        return first to second
    }

    /**
     * Checks whether at least one token is a standard 15-digit numeric IMEI.
     */
    fun hasStandard15DigitImei(raw: String): Boolean {
        if (raw.isBlank()) return false
        val tokens = raw.split("/", ",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        return tokens.any { token ->
            token.length == 15 && token.all { it.isDigit() }
        }
    }
}
