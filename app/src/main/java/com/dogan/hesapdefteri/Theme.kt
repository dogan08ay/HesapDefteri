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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
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
            .background(Brush.verticalGradient(listOf(Tan1, Tan2, Tan1, Tan2, Tan1)))
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
            .background(Brush.verticalGradient(listOf(Color(0xFF2E2E2E), Color(0xFF131313))))
            .drawBehind {
                val y = size.height - 4.dp.toPx()
                drawLine(
                    color = Color(0xB3FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                )
            }
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)
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
                Box(
                    Modifier.size(34.dp).border(1.5.dp, Color(0xCCFFFFFF), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menü", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

val SilverBorder = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFA2A2A2)))

@Composable
fun WhiteBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFECECEC))), shape)
            .border(BorderStroke(1.5.dp, SilverBorder), shape)
            .clip(shape),
        content = content
    )
}

@Composable
fun TypeIcon(type: AccountType, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.width / 28f
        val white = Color.White
        fun coin(x: Float, y: Float) {
            drawRoundRect(
                color = white,
                topLeft = Offset(x * k, y * k),
                size = Size(7f * k, 3.2f * k),
                cornerRadius = CornerRadius(1.2f * k, 1.2f * k)
            )
        }
        fun stack(x: Float, n: Int) {
            for (i in 1..n) coin(x, 24.8f - 4.2f * i)
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
            drawLine(
                color = white,
                start = Offset(x1 * k, y1 * k),
                end = Offset(x2 * k, y2 * k),
                strokeWidth = w * k,
                cap = StrokeCap.Round
            )
        }
        fun head(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val path = Path()
            path.moveTo(x1 * k, y1 * k)
            path.lineTo(x2 * k, y2 * k)
            path.lineTo(x3 * k, y3 * k)
            path.close()
            drawPath(path, white)
        }
        when (type) {
            AccountType.ASSET -> {
                stack(2f, 1); stack(10.5f, 2); stack(19f, 3)
            }
            AccountType.LIABILITY -> {
                stack(10.5f, 1); stack(19f, 2)
                drawCircle(color = white, radius = 6f * k, center = Offset(8f * k, 9f * k), style = Stroke(width = 1.8f * k))
                line(8f, 9f, 8f, 5.5f, 1.8f)
                line(8f, 9f, 11f, 10.5f, 1.8f)
            }
            AccountType.INCOME -> {
                stack(10.5f, 1); stack(19f, 2)
                line(3f, 3f, 11f, 11f, 2.2f)
                head(14f, 14f, 7.5f, 13f, 13f, 7.5f)
            }
            AccountType.EXPENSE -> {
                stack(2f, 2); stack(10.5f, 1)
                line(15f, 14f, 23f, 5f, 2.2f)
                head(25.5f, 2.5f, 18f, 4.5f, 24f, 10.5f)
            }
        }
    }
}

@Composable
fun CalendarIcon(modifier: Modifier = Modifier, tint: Color = GreenText) {
    Canvas(modifier) {
        val k = size.width / 28f
        drawRoundRect(
            color = tint,
            topLeft = Offset(3f * k, 6f * k),
            size = Size(22f * k, 19f * k),
            cornerRadius = CornerRadius(3f * k, 3f * k),
            style = Stroke(width = 1.8f * k)
        )
        drawRect(color = tint, topLeft = Offset(3f * k, 6f * k), size = Size(22f * k, 5f * k))
        drawLine(color = tint, start = Offset(9f * k, 3f * k), end = Offset(9f * k, 8f * k), strokeWidth = 1.8f * k, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(19f * k, 3f * k), end = Offset(19f * k, 8f * k), strokeWidth = 1.8f * k, cap = StrokeCap.Round)
        for (r in 0..1) {
            for (c in 0..2) {
                drawRect(
                    color = tint,
                    topLeft = Offset((6.5f + c * 6.2f) * k, (13.5f + r * 5f) * k),
                    size = Size(3.6f * k, 3f * k)
                )
            }
        }
    }
}

@Composable
fun CalendarBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(4.dp, CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD2D2D2))), CircleShape)
            .border(BorderStroke(1.5.dp, SilverBorder), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CalendarIcon(Modifier.fillMaxSize(0.58f))
    }
}

@Composable
fun CalcIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.width / 28f
        val c = Color(0xFF333333)
        val w = 2.2f * k
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(color = c, start = Offset(x1 * k, y1 * k), end = Offset(x2 * k, y2 * k), strokeWidth = w, cap = StrokeCap.Round)
        }
        line(3.5f, 7f, 10.5f, 7f); line(7f, 3.5f, 7f, 10.5f)
        line(17.5f, 7f, 24.5f, 7f)
        drawCircle(color = c, radius = 1.4f * k, center = Offset(21f * k, 3.6f * k))
        drawCircle(color = c, radius = 1.4f * k, center = Offset(21f * k, 10.4f * k))
        line(3.8f, 17.8f, 10.2f, 24.2f); line(10.2f, 17.8f, 3.8f, 24.2f)
        line(17.5f, 21f, 24.5f, 21f)
    }
}

fun cardShape(r: Float, cut: Float) = GenericShape { size, _ ->
    moveTo(r, 0f)
    lineTo(size.width - r, 0f)
    arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), -90f, 90f, false)
    lineTo(size.width, size.height - cut)
    lineTo(size.width - cut, size.height)
    lineTo(r, size.height)
    arcTo(Rect(0f, size.height - 2 * r, 2 * r, size.height), 90f, 90f, false)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
    close()
}

@Composable
fun TypeCard(
    type: AccountType,
    total: Long,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cut = 44.dp
    val outer = remember(density) { with(density) { cardShape(12.dp.toPx(), cut.toPx()) } }
    val inner = remember(density) { with(density) { cardShape(10.dp.toPx(), (cut - 4.dp).toPx()) } }
    val base = typeColor(type)
    Box(modifier.height(146.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(6.dp, outer)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFF9E9E9E))), outer)
                .padding(3.dp)
                .clip(inner)
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFDADADA))))
                .clickable(onClick = onOpen)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(lerp(base, Color.White, 0.28f), base, lerp(base, Color.Black, 0.35f))
                            )
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypeIcon(type, Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(type.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        money(total),
                        Modifier.align(Alignment.Center).padding(bottom = 10.dp),
                        color = if (total < 0) NegativeRed else base,
                        fontSize = 16.sp
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).size(cut).clickable(onClick = onAdd)) {
            Canvas(Modifier.fillMaxSize()) {
                val tri = Path()
                tri.moveTo(size.width, 0f)
                tri.lineTo(size.width, size.height)
                tri.lineTo(0f, size.height)
                tri.close()
                drawPath(
                    tri,
                    Brush.linearGradient(
                        listOf(Color(0xFFFAFAFA), Color(0xFFA9A9A9)),
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height)
                    )
                )
                val inset = 5.dp.toPx()
                val inner = Path()
                inner.moveTo(size.width - inset, inset * 1.6f)
                inner.lineTo(size.width - inset, size.height - inset)
                inner.lineTo(inset * 1.6f, size.height - inset)
                inner.close()
                drawPath(
                    inner,
                    Brush.linearGradient(
                        listOf(Color(0xFFEDEDED), Color(0xFFCFCFCF)),
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height)
                    )
                )
                drawPath(tri, Color(0xFF8C8C8C), style = Stroke(width = 2f))
                val c = Offset(size.width * 0.68f, size.height * 0.68f)
                val r = 6.dp.toPx()
                val sw = 3.6.dp.toPx()
                val shift = Offset(1.5.dp.toPx(), 1.5.dp.toPx())
                drawLine(Color.White, c - Offset(r, 0f) + shift, c + Offset(r, 0f) + shift, strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(Color.White, c - Offset(0f, r) + shift, c + Offset(0f, r) + shift, strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(Color(0xFF4A4A4A), c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(Color(0xFF4A4A4A), c - Offset(0f, r), c + Offset(0f, r), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun PeriodPill(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val f1 = DateTimeFormatter.ofPattern("dd MMM", TR)
    val f2 = DateTimeFormatter.ofPattern("dd MMM yyyy", TR)
    val label = month.atDay(1).format(f1) + " – " + month.atEndOfMonth().format(f2)
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp)
                .shadow(3.dp, shape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEDED))), shape)
                .border(BorderStroke(1.5.dp, SilverBorder), shape)
                .padding(end = 10.dp)
                .heightIn(min = 38.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(34.dp))
            Text(
                "‹",
                Modifier.clickable { onMonth(month.minusMonths(1)) }.padding(horizontal = 12.dp, vertical = 2.dp),
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
                Modifier.clickable { onMonth(month.plusMonths(1)) }.padding(horizontal = 12.dp, vertical = 2.dp),
                fontSize = 22.sp,
                color = GreenText
            )
        }
        CalendarBadge(Modifier.align(Alignment.CenterStart).size(46.dp))
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