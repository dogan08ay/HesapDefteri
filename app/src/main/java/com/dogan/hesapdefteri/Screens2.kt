@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
fun PinKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(50.dp)
            .shadow(3.dp, shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E2E2))), shape)
            .border(BorderStroke(1.5.dp, SilverBorder), shape)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = if (label.length > 1) 18.sp else 24.sp, color = Color(0xFF222222))
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
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LogoMark(Modifier.size(76.dp))
                Spacer(Modifier.height(10.dp))
                Text("Hesap Defteri", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenText)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                if (error) listOf(Color(0xFFC0392B), Color(0xFF8E2A20))
                                else listOf(Color(0xFF1E8A5E), Color(0xFF3F6E80))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (error) "Şifre hatalı, tekrar deneyin" else "Şifrenizi girin",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF7C7871), Color(0xFF55534E))))
                    .padding(horizontal = 34.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val boxes = if (savedLen > 0) savedLen else maxOf(4, pin.length)
                    for (i in 0 until boxes) {
                        val bshape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .shadow(3.dp, bshape)
                                .background(Color.White, bshape)
                                .border(BorderStroke(1.5.dp, SilverBorder), bshape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < pin.length) Text("●", fontSize = 26.sp, color = Color(0xFF222222))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key -> PinKey(key, Modifier.weight(1f)) { press(key) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinKey("Sil", Modifier.weight(1f)) { back() }
                    PinKey("0", Modifier.weight(1f)) { press("0") }
                    PinKey("Vazgeç", Modifier.weight(1f)) { pin = ""; error = false }
                }
                Spacer(Modifier.height(10.dp))
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
