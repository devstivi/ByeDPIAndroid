package io.github.dovecoteescapee.byedpi.core

import android.content.Context
import android.util.Log
import io.github.dovecoteescapee.byedpi.data.StrategyList
import io.github.dovecoteescapee.byedpi.data.parseStrategyList
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Этап 1 "обмена стратегиями" (issue #434): только чтение, без отправки каких-либо
 * данных пользователя. Приложение скачивает курируемый список стратегий, собранный
 * из открытых issues/discussions, и предлагает пользователю применить одну из них
 * вручную — ничего не применяется автоматически без действия пользователя.
 */
object StrategyRepository {
    private const val TAG = "StrategyRepository"
    private const val CACHE_FILE = "strategies_cache.json"
    private const val SEED_ASSET = "strategies_seed.json"

    // Зеркала одного и того же файла в этом форке — если одно недоступно (блокировка,
    // сбой), пробуем следующее. jsDelivr отдаёт GitHub-контент через свою CDN.
    private val SOURCES = listOf(
        "https://cdn.jsdelivr.net/gh/devstivi/ByeDPIAndroid@main/strategies/strategies.json",
        "https://raw.githubusercontent.com/devstivi/ByeDPIAndroid/main/strategies/strategies.json",
    )

    private const val TIMEOUT_MS = 8000

    fun load(context: Context): StrategyList {
        val fresh = fetchRemote()
        if (fresh != null) {
            runCatching { cacheFile(context).writeText(fresh, StandardCharsets.UTF_8) }
            return parseStrategyList(fresh)
        }

        val cached = runCatching { cacheFile(context).readText(StandardCharsets.UTF_8) }.getOrNull()
        if (cached != null) {
            return parseStrategyList(cached)
        }

        val seed = context.assets.open(SEED_ASSET).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return parseStrategyList(seed)
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

    private fun fetchRemote(): String? {
        for (url in SOURCES) {
            val result = runCatching { fetch(url) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun fetch(urlStr: String): String? {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Unexpected response ${connection.responseCode} from $urlStr")
                return null
            }

            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch $urlStr: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }
}
