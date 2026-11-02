@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr-TR")

val Tan1 = Color(0xFFE6DCC8)
val Tan2 = Color(0xFFC8BA9C)
val Dark = Color(0xFF1F1F1F)
val GreenText = Color(0xFF2E5E1A)
val NegativeRed = Color(0xFFD9534F)

val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 13.sp),
    bodyMedium = TextStyle(fontSize = 12.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
)

val PALETTE = listOf(
    Color(0xFF4E8F6B), Color(0xFFB85C5C), Color(0xFF6BA6C9), Color(0xFFD9A441),
    Color(0xFF8A6BB5), Color(0xFF7A7A7A), Color(0xFF5C8DB8), Color(0xFFC77D3A)
)

fun typeColor(t: AccountType): Color = when (t) {
    AccountType.ASSET -> Color(0xFF3F6E80)
    AccountType.LIABILITY -> Color(0xFF585858)
    AccountType.INCOME -> Color(0xFF1E8A5E)
    AccountType.EXPENSE -> Color(0xFFA32A1C)
}

fun money(cents: Long): String = String.format(TR, "%,.2f TL", cents / 100.0)

fun dayText(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tan1, Tan2, Tan1)))
    ) { content() }
}

@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.width / 108f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF2E8B57), Color(0xFF14452B))),
            cornerRadius = CornerRadius(24f * k, 24f * k)
        )
        fun poly(vararg p: Float): Path {
            val path = Path()
            path.moveTo(p[0] * k, p[1] * k)
            var i = 2
            while (i < p.size) {
                path.lineTo(p[i] * k, p[i + 1] * k)
                i += 2
            }
            path.close()
            return path
        }
        drawPath(poly(52.5f, 37f, 30f, 41f, 30f, 74f, 52.5f, 70.5f), Color(0xFFF4EBD6))
        drawPath(poly(55.5f, 37f, 78f, 41f, 78f, 74f, 55.5f, 70.5f), Color(0xFFF4EBD6))
        val line = Path()
        line.moveTo(36f * k, 64f * k)
        line.lineTo(47f * k, 53f * k)
        line.lineTo(55f * k, 60f * k)
        line.lineTo(71f * k, 44f * k)
        drawPath(line, Color(0xFFE09A12), style = Stroke(width = 4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(poly(78f, 37f, 67f, 39f, 76f, 48f), Color(0xFFE09A12))
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    onHome: (() -> Unit)?,
    onMenu: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    logo: Boolean = false
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Dark)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .heightIn(min = 48.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
            }
        }
        if (logo) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                LogoMark(Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
            )
        }
        if (onHome != null || onEdit != null) {
            Row(Modifier.align(Alignment.CenterEnd)) {
                if (onHome != null) {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "Ana sayfa", tint = Color.White)
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = Color.White)
                    }
                }
            }
        }
        if (onMenu != null) {
            IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Menu, contentDescription = "Menü", tint = Color.White)
            }
        }
    }
}

@Composable
fun WhiteBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .background(Color.White, RoundedCornerShape(10.dp)),
        content = content
    )
}

@Composable
fun PeriodPill(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val f1 = DateTimeFormatter.ofPattern("dd MMM", TR)
    val f2 = DateTimeFormatter.ofPattern("dd MMM yyyy", TR)
    val label = month.atDay(1).format(f1) + " – " + month.atEndOfMonth().format(f2)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📅", fontSize = 20.sp)
        Text(
            "‹",
            Modifier.clickable { onMonth(month.minusMonths(1)) }.padding(horizontal = 14.dp, vertical = 2.dp),
            fontSize = 22.sp,
            color = GreenText
        )
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = GreenText,
            fontSize = 12.sp
        )
        Text(
            "›",
            Modifier.clickable { onMonth(month.plusMonths(1)) }.padding(horizontal = 14.dp, vertical = 2.dp),
            fontSize = 22.sp,
            color = GreenText
        )
    }
}

@Composable
fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    textAlign: TextAlign = TextAlign.Start,
    suffix: String? = null,
    flat: Boolean = false
) {
    val shape = RoundedCornerShape(10.dp)
    val base = if (flat) Modifier.background(Color(0xFFF3F3F3), shape) else Modifier.shadow(3.dp, shape).background(Color.White, shape)
    Row(
        Modifier.fillMaxWidth().then(base).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, Modifier.fillMaxWidth(), color = Color.Gray, fontSize = 14.sp, textAlign = textAlign)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Color.Black, textAlign = textAlign),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (suffix != null) {
            Spacer(Modifier.width(6.dp))
            Text(suffix, fontSize = 14.sp)
        }
    }
}

@Composable
fun AddPanel(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.72f))), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun ChartIconButton(pie: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .shadow(3.dp, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) {
            if (pie) {
                drawArc(color = PALETTE[0], startAngle = -90f, sweepAngle = 200f, useCenter = true)
                drawArc(color = PALETTE[1], startAngle = 110f, sweepAngle = 100f, useCenter = true)
                drawArc(color = PALETTE[2], startAngle = 210f, sweepAngle = 60f, useCenter = true)
            } else {
                val w = size.width / 5f
                listOf(0.5f, 1f, 0.7f).forEachIndexed { i, h ->
                    val barH = size.height * h
                    drawRect(
                        color = PALETTE[i],
                        topLeft = Offset(w * (0.5f + i * 1.6f), size.height - barH),
                        size = Size(w, barH)
                    )
                }
            }
        }
    }
}

@Composable
fun PieChart(slices: List<Pair<String, Long>>) {
    val total = slices.sumOf { it.second }.toFloat()
    Canvas(Modifier.size(220.dp)) {
        var start = -90f
        slices.forEachIndexed { i, s ->
            val sweep = s.second / total * 360f
            drawArc(
                color = PALETTE[i % PALETTE.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true
            )
            start += sweep
        }
    }
}

@Composable
fun BarChart(slices: List<Pair<String, Long>>) {
    val max = slices.maxOf { it.second }.toFloat()
    Canvas(Modifier.size(width = 300.dp, height = 220.dp)) {
        val bw = size.width / (slices.size * 2f)
        slices.forEachIndexed { i, s ->
            val h = s.second / max * size.height
            drawRect(
                color = PALETTE[i % PALETTE.size],
                topLeft = Offset(bw * (2 * i + 0.5f), size.height - h),
                size = Size(bw, h)
            )
        }
    }
}
