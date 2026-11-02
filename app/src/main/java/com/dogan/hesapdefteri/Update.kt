@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "dogan08ay/HesapDefteri"

    data class Release(val code: Int, val tag: String, val apkUrl: String)

    @Suppress("DEPRECATION")
    fun currentCode(ctx: Context): Long {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    }

    /** En son yayınlanan sürümü getirir. Sürüm yoksa null, ağ/izin sorununda istisna fırlatır. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val status = conn.responseCode
        if (status == 404) return@withContext null
        if (status != 200) throw IllegalStateException("GitHub yanıt kodu $status")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tag = json.getString("tag_name")
        val code = tag.substringAfter("build-").toIntOrNull() ?: return@withContext null
        val assets = json.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
        }
        if (url == null) null else Release(code, tag, url)
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()
        if (conn.responseCode != 200) throw IllegalStateException("İndirme hatası ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(16384)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress(read.toFloat() / total)
                }
            }
        }
        out
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun install(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("hesap.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Kurulum tamamlanamadı."
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun UpdateScreen(known: Updater.Release?, onBack: () -> Unit, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentCode(ctx) }
    var release by remember { mutableStateOf(known) }
    var status by remember {
        mutableStateOf(if (known != null) "Yeni sürüm var: build ${known.code}" else "")
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Güncelleme", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Kurulu sürüm: build $current", fontWeight = FontWeight.Bold)
                        if (status.isNotEmpty()) Text(status)
                        progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
                        Button(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    status = "Kontrol ediliyor…"
                                    try {
                                        val r = Updater.latest()
                                        if (r != null && r.code > current) {
                                            release = r
                                            status = "Yeni sürüm var: build ${r.code}"
                                        } else {
                                            release = null
                                            status = if (r == null) "Yayınlanmış sürüm bulunamadı." else "Uygulama güncel."
                                        }
                                    } catch (e: Exception) {
                                        status = "Kontrol edilemedi: ${e.message}"
                                    }
                                    busy = false
                                }
                            }
                        ) { Text("Güncellemeyi kontrol et") }
                        release?.let { r ->
                            Button(
                                enabled = !busy,
                                onClick = {
                                    if (!Updater.canInstall(ctx)) {
                                        status = "Önce açılan ekranda bu uygulamaya kurulum izni ver, sonra geri dönüp tekrar dene."
                                        Updater.openInstallPermission(ctx)
                                    } else {
                                        scope.launch {
                                            busy = true
                                            status = "İndiriliyor…"
                                            try {
                                                val f = Updater.download(ctx, r.apkUrl) { progress = it }
                                                status = "Kurulum ekranı açılıyor…"
                                                Updater.install(ctx, f)
                                            } catch (e: Exception) {
                                                status = "Hata: ${e.message}"
                                            }
                                            progress = null
                                            busy = false
                                        }
                                    }
                                }
                            ) { Text("İndir ve kur (build ${r.code})") }
                        }
                    }
                }
            }
        }
    }
}
