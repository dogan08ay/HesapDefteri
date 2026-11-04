package com.dogan.hesapdefteri

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

fun csvEscape(s: String): String =
    if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

fun amountCsv(cents: Long): String = String.format(TR, "%.2f", cents / 100.0)

fun kindOf(from: Account?, to: Account?): String = when {
    from?.type == AccountType.INCOME -> "Gelir"
    to?.type == AccountType.EXPENSE -> "Gider"
    from?.type == AccountType.ASSET && to?.type == AccountType.LIABILITY -> "Ödeme"
    from?.type == AccountType.LIABILITY && to?.type == AccountType.ASSET -> "Borçlanma"
    else -> "Aktarım"
}

/** Excel'de açılabilen (noktalı virgül ayraçlı) işlem listesi. */
fun txnsToCsv(
    list: List<Txn>,
    byId: Map<Long, Account>,
    extraHeader: String? = null,
    extra: ((Txn) -> String)? = null
): String {
    val sb = StringBuilder()
    sb.append("Tarih;Tür;Kaynak hesap;Hedef hesap;Kategori yolu;Açıklama;Tutar (TL)")
    if (extraHeader != null) sb.append(';').append(csvEscape(extraHeader))
    sb.append("\r\n")
    list.sortedBy { it.date }.forEach { t ->
        val f = byId[t.fromId]
        val to = byId[t.toId]
        val cat = when {
            to?.type == AccountType.EXPENSE -> to
            f?.type == AccountType.INCOME -> f
            else -> to ?: f
        }
        val path = if (cat != null) trail(cat, byId) + " › " + cat.name else ""
        sb.append(dayText(t.date)).append(';')
            .append(kindOf(f, to)).append(';')
            .append(csvEscape(f?.name ?: "?")).append(';')
            .append(csvEscape(to?.name ?: "?")).append(';')
            .append(csvEscape(path)).append(';')
            .append(csvEscape(t.note)).append(';')
            .append(amountCsv(t.cents))
        if (extra != null) sb.append(';').append(csvEscape(extra(t)))
        sb.append("\r\n")
    }
    return sb.toString()
}

fun csvFileName(base: String): String {
    val safe = base.lowercase(TR).replace(Regex("[^a-z0-9çğıöşü]+"), "-").trim('-').ifEmpty { "liste" }
    return "hesap-defteri-$safe-${LocalDate.now()}.csv"
}

fun writeCsv(ctx: Context, uri: Uri, text: String) {
    ctx.contentResolver.openOutputStream(uri)?.use {
        it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        it.write(text.toByteArray(Charsets.UTF_8))
    }
}

/** Dosya kaydetme penceresini açan bir işlev döndürür; içerik, kayıt anında üretilir. */
@Composable
fun rememberCsvSaver(build: () -> String): (String) -> Unit {
    val ctx = LocalContext.current
    val latest = rememberUpdatedState(build)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                writeCsv(ctx, uri, latest.value())
                Toast.makeText(ctx, "Dosya kaydedildi.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Dosya kaydedilemedi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    return { base -> launcher.launch(csvFileName(base)) }
}

@Composable
fun CsvLink(fileBase: String, modifier: Modifier = Modifier, build: () -> String) {
    val save = rememberCsvSaver(build)
    Text(
        "⤓ CSV",
        modifier.clickable { save(fileBase) }.padding(horizontal = 10.dp, vertical = 6.dp),
        color = GreenText,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
}

@Composable
fun CsvPanel(label: String, fileBase: String, build: () -> String) {
    val save = rememberCsvSaver(build)
    OutlinedButton(onClick = { save(fileBase) }, modifier = Modifier.fillMaxWidth()) {
        Text("⤓ $label", fontSize = 13.sp)
    }
}
