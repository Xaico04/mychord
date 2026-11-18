package com.hichord.app
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "Xaico04/mychord"

    class Info(val version: Long, val tag: String, val url: String, val size: Long)

    /** Consulta a ultima release do GitHub. Retorna null se nao houver APK; lanca excecao se falhar a rede. */
    fun fetchLatest(): Info? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "HiChord-App")
        c.connectTimeout = 15000; c.readTimeout = 15000
        if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = j.getString("tag_name")
        val v = tag.filter { it.isDigit() }.toLongOrNull() ?: return null
        val assets = j.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) return Info(v, tag, a.getString("browser_download_url"), a.getLong("size"))
        }
        return null
    }

    /** Baixa o APK inteiro de uma vez. Chama onProgress(0..100). */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "update"); dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "HiChord.apk")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "HiChord-App")
        c.connectTimeout = 15000; c.readTimeout = 30000
        if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
        c.inputStream.use { inp ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                    if (pct != last) { last = pct; onProgress(pct) }
                }
            }
        }
        if (total > 0 && f.length() != total) { f.delete(); throw IOException("download incompleto") }
        return f
    }
}
