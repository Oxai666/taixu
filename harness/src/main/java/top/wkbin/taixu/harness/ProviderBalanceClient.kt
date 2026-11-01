package top.wkbin.taixu.harness

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 余额查询：向 OpenAI 兼容服务商要账户余额。
 *
 * 没有统一的余额协议，各家路径与字段都不一样，所以这里按「已知形状依次探测」：
 *  1. DeepSeek / 多数中转站：`GET {base}/user/balance` → `balance_infos[0].total_balance`
 *  2. 部分中转：`GET {base}/user/balance` → `total_available` 或 `balance`
 *  3. 另一批中转：`GET {base}/v1/credits` → `data.total_credits - data.total_usage`
 *
 * 全部失败就返回 null —— 余额是锦上添花，拿不到就静默不显示，
 * 绝不让它变成一个红叉噪音或阻塞模型选择面板。
 */
object ProviderBalanceClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 查询余额并返回形如 `¥12.34` 的串；拿不到返回 null。
     *
     * [apiPath] 非空时**只**打这一个路径（用户在模型档案里显式配了就以他的为准，
     * 不再乱试其它端点）；[resultPath] 非空时按它取值，否则退回内置字段链。
     */
    suspend fun fetch(
        baseUrl: String,
        apiKey: String,
        currency: String = "¥",
        apiPath: String = "",
        resultPath: String = "",
    ): String? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return@withContext null
        val root = baseUrl.trimEnd('/')
        resultPath.trim().takeIf { it.isNotEmpty() }?.let { path ->
            customUrls(root, apiPath).firstNotNullOfOrNull { url ->
                getJson(url, apiKey)?.let { firstNumber(json.parseToJsonElement(it).jsonObject, path) }
            }?.let { return@withContext currency + format(it) }
        }
        if (apiPath.isNotBlank()) {
            return@withContext customUrls(root, apiPath).firstNotNullOfOrNull { url ->
                getJson(url, apiKey)?.let { parseAmount(it) }
            }?.let { currency + format(it) }
        }
        // 未配置自定义路径：按已知形状依次探测
        val candidates = buildList {
            add("$root/user/balance")
            if (!root.endsWith("/v1")) add("$root/v1/user/balance")
            add("$root/v1/credits")
            if (!root.endsWith("/v1")) add("$root/credits")
        }.distinct()
        for (url in candidates) {
            val text = getJson(url, apiKey) ?: continue
            parseAmount(text)?.let { return@withContext currency + format(it) }
        }
        null
    }

    /** 自定义路径的候选 URL：绝对地址直接用，相对地址拼 baseUrl（带/不带 /v1 各试一次）。 */
    private fun customUrls(root: String, apiPath: String): List<String> {
        val path = apiPath.trim()
        if (path.startsWith("http")) return listOf(path)
        val normalized = if (path.startsWith("/")) path else "/$path"
        return if (root.endsWith("/v1")) listOf("$root$normalized")
        else listOf("$root$normalized", "$root/v1$normalized")
    }

    private fun getJson(url: String, apiKey: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (_: Throwable) {
        null
    }

    /** 按已知字段路径取金额，取不到返回 null。 */
    private fun parseAmount(body: String): Double? = try {
        val root = json.parseToJsonElement(body).jsonObject
        firstNumber(root, "balance_infos[0].total_balance")
            ?: firstNumber(root, "balance_infos[0].total_available")
            ?: firstNumber(root, "total_available")
            ?: firstNumber(root, "balance")
            ?: creditDelta(root)
    } catch (_: Throwable) {
        null
    }

    private fun creditDelta(root: JsonObject): Double? {
        val data = root["data"]?.jsonObject ?: return null
        val total = data["total_credits"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return null
        val used = data["total_usage"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
        return total - used
    }

    private fun firstNumber(root: JsonObject, path: String): Double? = try {
        var node: kotlinx.serialization.json.JsonElement = root
        for (segment in path.split('.')) {
            if (segment.contains('[')) {
                val name = segment.substringBefore('[')
                val index = segment.substringAfter('[').substringBefore(']').toInt()
                if (name.isNotEmpty()) node = node.jsonObject[name] ?: return null
                node = node.jsonArray[index]
            } else {
                node = node.jsonObject[segment] ?: return null
            }
        }
        node.jsonPrimitive.contentOrNull?.toDoubleOrNull()
    } catch (_: Throwable) {
        null
    }

    private fun format(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
}
