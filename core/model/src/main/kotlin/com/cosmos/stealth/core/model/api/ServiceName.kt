package com.cosmos.stealth.core.model.api


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Supported services
 *
 * Values: reddit,teddit,lemmy
 */
@Suppress("EnumNaming")
@JsonClass(generateAdapter = false)
enum class ServiceName(val value: String) {

    @Json(name = "reddit")
    reddit("reddit"),

    @Json(name = "teddit")
    teddit("teddit"),

    @Json(name = "redlib")
    redlib("redlib"),

    @Json(name = "lemmy")
    lemmy("lemmy");

    override fun toString(): String = value

    companion object {
        /**
         * Converts the provided [data] to a [String] on success, null otherwise.
         */
        fun encode(data: Any?): String? = if (data is ServiceName) "$data" else null

        /**
         * Returns a valid [ServiceName] for [data], null otherwise.
         */
        fun decode(data: Any?): ServiceName? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

