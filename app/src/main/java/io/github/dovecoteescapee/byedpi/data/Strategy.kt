package io.github.dovecoteescapee.byedpi.data

import org.json.JSONArray
import org.json.JSONObject

data class Strategy(
    val id: String,
    val operators: List<String>,
    val args: String,
    val notes: String?,
    val confirmations: Int,
    val sources: List<String>,
)

data class StrategyList(
    val updated: String?,
    val strategies: List<Strategy>,
)

fun parseStrategyList(json: String): StrategyList {
    val root = JSONObject(json)
    val updated = root.optString("updated").ifBlank { null }
    val arr = root.optJSONArray("strategies") ?: JSONArray()

    val strategies = (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val args = obj.optString("args", "")
        val id = obj.optString("id", "")
        if (args.isBlank() || id.isBlank()) return@mapNotNull null

        Strategy(
            id = id,
            operators = obj.optJSONArray("operators")?.toStringList() ?: emptyList(),
            args = args,
            notes = obj.optString("notes", "").ifBlank { null },
            confirmations = obj.optInt("confirmations", 0),
            sources = obj.optJSONArray("sources")?.toStringList() ?: emptyList(),
        )
    }

    return StrategyList(updated, strategies)
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { optString(it, null) }
