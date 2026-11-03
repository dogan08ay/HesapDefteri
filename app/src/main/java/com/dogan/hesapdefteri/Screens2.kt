@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

object PinStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("hesap-defteri:$pin").toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains("h")
    fun set(ctx: Context, pin: String) { prefs(ctx).edit().putString("h", hash(pin)).apply() }
    fun clear(ctx: Context) { prefs(ctx).edit().remove("h").apply() }
    fun check(ctx: Context, pin: String): Boolean = prefs(ctx).getString("h", null) == hash(pin)
}

@Composable
fun InfoScreen(title: String, body: String, onBack: () -> Unit, onHome: () -> Unit) {
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, onBack, onHome)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                WhiteBox { Text(body, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> String,
    onImport: (String, (Boolean, String) -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(onExport().toByteArray(Charsets.UTF_8)) }
                message = "Yedek kaydedildi."
            } catch (e: Exception) {
                message = "Yedek kaydedilemedi: ${e.message}"
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text == null) {
                    message = "Dosya okunamadı."
                } else {
                    onImport(text) { ok, msg -> message = msg }
                }
            } catch (e: Exception) {
                message = "Dosya okunamadı: ${e.message}"
            }
        }
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Ayarlar", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Yedekleme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Veriler yalnızca bu telefonda durur. Uygulamayı silersen ya da telefonu değiştirirsen kaybolur, bu yüzden arada yedek al.",
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { saveLauncher.launch("hesap-defteri-yedek-${LocalDate.now()}.json") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Yedek al") }
                        OutlinedButton(
                            onClick = { confirmImport = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Yedekten geri yükle") }
                        message?.let { Text(it, fontSize = 12.sp, color = GreenText) }
                    }
                }
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Veriler", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Tüm hesaplar, işlemler ve kısayollar bu cihazda saklanır.", fontSize = 12.sp)
                        Button(
                            onClick = { confirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = typeColor(AccountType.EXPENSE))
                        ) { Text("Tüm verileri sil") }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Emin misin?") },
            text = { Text("Tüm işlemler ve kısayollar silinecek, varsayılan kategoriler yeniden yüklenecek. Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { onReset(); confirm = false }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } }
        )
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Yedekten geri yüklensin mi?") },
            text = { Text("Şu anki tüm veriler silinip yedekteki verilerle değiştirilecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    openLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }) { Text("Devam") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Hesap Defteri", null, null)
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("PIN kodunu gir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it; error = false },
                            label = { Text("PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = error,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error) Text("PIN hatalı.", color = MaterialTheme.colorScheme.error)
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (PinStore.check(ctx, pin)) onUnlock() else error = true }
                        ) { Text("Aç") }
                    }
                }
            }
        }
    }
}

@Composable
fun PinSetupScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val ctx = LocalContext.current
    var hasPin by remember { mutableStateOf(PinStore.isSet(ctx)) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Uygulama kilidi", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (hasPin) "Kilit açık. Uygulama her açılışta PIN soracak." else "Kilit kapalı.",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Bu bir uygulama kilididir, veritabanını şifrelemez. Telefonu başkasına verdiğinde uygulamanın açılmasını engeller.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() } },
                            label = { Text("Yeni PIN (4-8 rakam)") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pin2, onValueChange = { pin2 = it.filter { c -> c.isDigit() } },
                            label = { Text("PIN tekrar") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        msg?.let { Text(it) }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                when {
                                    pin.length < 4 || pin.length > 8 -> msg = "PIN 4 ile 8 rakam arasında olmalı."
                                    pin != pin2 -> msg = "PIN'ler eşleşmiyor."
                                    else -> {
                                        PinStore.set(ctx, pin)
                                        hasPin = true
                                        pin = ""
                                        pin2 = ""
                                        msg = "PIN kaydedildi."
                                    }
                                }
                            }
                        ) { Text(if (hasPin) "PIN'i değiştir" else "PIN'i kaydet") }
                        if (hasPin) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { PinStore.clear(ctx); hasPin = false; msg = "Kilit kaldırıldı." }
                            ) { Text("Kilidi kaldır") }
                        }
                    }
                }
            }
        }
    }
}
