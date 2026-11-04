@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
    fun set(ctx: Context, pin: String) { prefs(ctx).edit().putString("h", hash(pin)).putInt("len", pin.length).apply() }
    fun length(ctx: Context): Int = prefs(ctx).getInt("len", 0)
    fun clear(ctx: Context) { prefs(ctx).edit().remove("h").remove("len").apply() }
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
fun SettingsScreen(onBack: () -> Unit, onHome: () -> Unit, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Ayarlar", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Veriler", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tüm hesaplar, işlemler ve kısayollar bu cihazda saklanır. Yedek almak için yan menüdeki “Verileri yedekle”yi kullan.",
                            fontSize = 12.sp
                        )
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
}

@Composable
fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val savedLen = remember { PinStore.length(ctx) }
    val maxLen = if (savedLen > 0) savedLen else 8
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun press(d: String) {
        if (pin.length >= maxLen) return
        error = false
        val next = pin + d
        pin = next
        if (savedLen > 0) {
            if (next.length == savedLen) {
                if (PinStore.check(ctx, next)) onUnlock() else { error = true; pin = "" }
            }
        } else {
            if (next.length >= 4 && PinStore.check(ctx, next)) onUnlock()
            else if (next.length >= maxLen) { error = true; pin = "" }
        }
    }
    fun back() {
        if (pin.isNotEmpty()) { pin = pin.dropLast(1); error = false }
    }

    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            LogoMark(Modifier.size(76.dp))
            Spacer(Modifier.height(10.dp))
            Text("Hesap Defteri", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenText)
            Spacer(Modifier.height(4.dp))
            Text(
                if (error) "PIN hatalı, tekrar dene" else "PIN kodunu gir",
                fontSize = 13.sp,
                color = if (error) NegativeRed else Color.Gray
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val dots = if (savedLen > 0) savedLen else maxOf(4, pin.length)
                for (i in 0 until dots) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < pin.length) GreenText else Color(0x33000000))
                    )
                }
            }
            Spacer(Modifier.weight(0.7f))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) Spacer(Modifier.size(76.dp))
                            else PinKey(key) { if (key == "⌫") back() else press(key) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
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
