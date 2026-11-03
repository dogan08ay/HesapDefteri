@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

val PASTEL = listOf(
    Color(0xFFC5F982), Color(0xFFFFF48D), Color(0xFFFFD08A), Color(0xFF8EE3FF),
    Color(0xFFFF8FA3), Color(0xFFB9A7FF), Color(0xFFA8E6CF), Color(0xFFFFB3D9)
)

val BlueMain = Color(0xFF1565C0)
val RedMain = Color(0xFFC62828)

enum class Span(val label: String) {
    DAY("Günlük"), WEEK("Haftalık"), MONTH("Aylık"), YEAR("Yıllık"), ALL("Hepsi")
}

fun spanRange(span: Span, a: LocalDate): Pair<Long, Long> = when (span) {
    Span.DAY -> a.toEpochDay() to a.toEpochDay()
    Span.WEEK -> {
        val s = a.minusDays((a.dayOfWeek.value - 1).toLong())
        s.toEpochDay() to s.plusDays(6).toEpochDay()
    }
    Span.MONTH -> {
        val ym = YearMonth.from(a)
        ym.atDay(1).toEpochDay() to ym.atEndOfMonth().toEpochDay()
    }
    Span.YEAR -> LocalDate.of(a.year, 1, 1).toEpochDay() to LocalDate.of(a.year, 12, 31).toEpochDay()
    Span.ALL -> (Long.MIN_VALUE / 2) to (Long.MAX_VALUE / 2)
}

fun shiftAnchor(span: Span, a: LocalDate, dir: Int): LocalDate = when (span) {
    Span.DAY -> a.plusDays(dir.toLong())
    Span.WEEK -> a.plusWeeks(dir.toLong())
    Span.MONTH -> a.plusMonths(dir.toLong())
    Span.YEAR -> a.plusYears(dir.toLong())
    Span.ALL -> a
}

fun rangeLabel(span: Span, r: Pair<Long, Long>): String {
    if (span == Span.ALL) return "Tüm zamanlar"
    val f = DateTimeFormatter.ofPattern("dd MMM yyyy", TR)
    val s = LocalDate.ofEpochDay(r.first)
    val e = LocalDate.ofEpochDay(r.second)
    return if (span == Span.DAY) s.format(f) else s.format(f) + " – " + e.format(f)
}

fun statusTitle(span: Span, anchor: LocalDate): String = when (span) {
    Span.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", TR))
    Span.YEAR -> anchor.year.toString()
    Span.DAY -> anchor.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", TR))
    Span.WEEK -> rangeLabel(Span.WEEK, spanRange(Span.WEEK, anchor))
    Span.ALL -> "Tüm zamanlar"
}

data class FlowTotals(
    val income: Long,
    val plannedIncome: Long,
    val expense: Long,
    val unpaidExpense: Long,
    val received: Long,
    val plannedReceived: Long,
    val paidOut: Long,
    val plannedPaid: Long,
    val cardSpend: Long,
    val debtPayments: Long
)

/**
 * Kâr: aralıktaki tüm gelir ve giderler (ödenmiş ya da ödenmemiş).
 * Nakit akışı: varlık hesaplarına giren (alınan) ve çıkan (ödenen) paralar; bugünden sonrası "bekleyen"dir.
 * Ödenmemiş gider: kartla/borçla yapılan harcama + ileri tarihli planlı giderler.
 */
fun flowTotals(
    accounts: List<Account>,
    txns: List<Txn>,
    r: Pair<Long, Long>,
    today: Long = LocalDate.now().toEpochDay()
): FlowTotals {
    val byId = accounts.associateBy { it.id }
    var income = 0L
    var plannedIncome = 0L
    var expense = 0L
    var unpaid = 0L
    var received = 0L
    var plannedReceived = 0L
    var paidOut = 0L
    var plannedPaid = 0L
    var card = 0L
    var debtPay = 0L
    for (t in txns) {
        if (t.date < r.first || t.date > r.second) continue
        val f = byId[t.fromId]?.type
        val to = byId[t.toId]?.type
        val future = t.date > today
        if (f == AccountType.INCOME) {
            income += t.cents
            if (future) plannedIncome += t.cents
            if (to == AccountType.ASSET) {
                if (future) plannedReceived += t.cents else received += t.cents
            }
        }
        if (to == AccountType.EXPENSE) {
            expense += t.cents
            if (f == AccountType.LIABILITY) {
                card += t.cents
                unpaid += t.cents
            } else if (f == AccountType.ASSET) {
                if (future) {
                    unpaid += t.cents
                    plannedPaid += t.cents
                } else {
                    paidOut += t.cents
                }
            }
        }
        if (f == AccountType.ASSET && to == AccountType.LIABILITY) {
            debtPay += t.cents
            if (future) plannedPaid += t.cents else paidOut += t.cents
        }
    }
    return FlowTotals(income, plannedIncome, expense, unpaid, received, plannedReceived, paidOut, plannedPaid, card, debtPay)
}

/** Gelir/gider hesaplarının seçili aralıktaki kendi tutarları. */
fun periodTotals(accounts: List<Account>, txns: List<Txn>, r: Pair<Long, Long>): Map<Long, Long> {
    val byId = accounts.associateBy { it.id }
    val res = HashMap<Long, Long>()
    for (t in txns) {
        if (t.date < r.first || t.date > r.second) continue
        val from = byId[t.fromId]
        val to = byId[t.toId]
        if (from != null && (from.type == AccountType.INCOME || from.type == AccountType.EXPENSE)) {
            res[from.id] = (res[from.id] ?: 0L) + deltaFor(from, false, t.cents)
        }
        if (to != null && (to.type == AccountType.INCOME || to.type == AccountType.EXPENSE)) {
            res[to.id] = (res[to.id] ?: 0L) + deltaFor(to, true, t.cents)
        }
    }
    return res
}

data class Slice(val name: String, val cents: Long, val drillGroup: String? = null)

@Composable
fun BellIcon(color: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path()
        p.moveTo(w * 0.16f, h * 0.74f)
        p.cubicTo(w * 0.30f, h * 0.62f, w * 0.26f, h * 0.30f, w * 0.50f, h * 0.18f)
        p.cubicTo(w * 0.74f, h * 0.30f, w * 0.70f, h * 0.62f, w * 0.84f, h * 0.74f)
        p.close()
        drawPath(p, color)
        drawCircle(color = color, radius = w * 0.10f, center = Offset(w * 0.5f, h * 0.88f))
        drawCircle(color = color, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.12f))
    }
}

@Composable
fun BellLine(cents: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BellIcon(color)
        Spacer(Modifier.width(4.dp))
        Text(money(cents), color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DonutChart(
    slices: List<Slice>,
    totalColor: Color,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val total = slices.sumOf { it.cents }.toFloat()
    val sel = slices.firstOrNull { it.name == selected }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 320.dp)
        Box(
            Modifier.size(side).pointerInput(slices, selected) {
                detectTapGestures { off ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = off.x - cx
                    val dy = off.y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    val r = minOf(size.width, size.height) / 2f
                    if (dist > r || dist < r * 0.55f) {
                        onSelect(null)
                    } else {
                        var ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (ang < 0f) ang += 360f
                        var acc = 0f
                        var hit: String? = null
                        for (s in slices) {
                            val sw = s.cents / total * 360f
                            if (ang >= acc && ang < acc + sw) {
                                hit = s.name
                                break
                            }
                            acc += sw
                        }
                        onSelect(if (hit == selected) null else hit)
                    }
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = s.cents / total * 360f
                    val alpha = if (selected == null || selected == s.name) 1f else 0.4f
                    drawArc(
                        color = PASTEL[i % PASTEL.size].copy(alpha = alpha),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    start += sweep
                }
                var a = -90f
                slices.forEach { s ->
                    val rad = Math.toRadians(a.toDouble())
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(center.x + (r * cos(rad)).toFloat(), center.y + (r * sin(rad)).toFloat()),
                        strokeWidth = 3f
                    )
                    a += s.cents / total * 360f
                }
                drawCircle(color = Color.White, radius = r * 0.55f)
                if (selected != null) {
                    var st = -90f
                    for (s in slices) {
                        val sw = s.cents / total * 360f
                        if (s.name == selected) {
                            drawArc(
                                color = Color(0xFF1E5E3A),
                                startAngle = st,
                                sweepAngle = sw,
                                useCenter = false,
                                topLeft = Offset(4f, 4f),
                                size = Size(size.width - 8f, size.height - 8f),
                                style = Stroke(width = 6f)
                            )
                        }
                        st += sw
                    }
                }
            }
            val half = side.value / 2f
            var acc = -90f
            slices.forEach { s ->
                val sweep = s.cents / total * 360f
                val mid = acc + sweep / 2f
                acc += sweep
                if (sweep >= 24f) {
                    val rad = Math.toRadians(mid.toDouble())
                    val rr = half * 0.775f
                    val cx = half + rr * cos(rad).toFloat()
                    val cy = half + rr * sin(rad).toFloat()
                    Box(
                        Modifier.offset(x = (cx - 40f).dp, y = (cy - 18f).dp).size(width = 80.dp, height = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "%" + (s.cents / total * 100f).roundToInt() + "\n" + s.name,
                            fontSize = 11.sp,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(
                Modifier.align(Alignment.Center).width(side * 0.5f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (sel != null) {
                    Text(
                        sel.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(money(sel.cents), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = totalColor)
                    Text("%" + (sel.cents / total * 100f).roundToInt(), fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text("Toplam", fontSize = 12.sp, color = totalColor)
                    Text(money(slices.sumOf { it.cents }), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = totalColor)
                    Text("dilime dokun", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun PastelBars(slices: List<Slice>, selected: String?) {
    val max = slices.maxOf { it.cents }.toFloat()
    Canvas(Modifier.size(width = 300.dp, height = 220.dp)) {
        val bw = size.width / (slices.size * 2f)
        slices.forEachIndexed { i, s ->
            val h = s.cents / max * size.height
            val alpha = if (selected == null || selected == s.name) 1f else 0.4f
            drawRoundRect(
                color = PASTEL[i % PASTEL.size].copy(alpha = alpha),
                topLeft = Offset(bw * (2 * i + 0.5f), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

@Composable
fun FlowBars(left: Long, right: Long, rightPart: Long, leftColor: Color, rightColor: Color) {
    Canvas(Modifier.size(width = 70.dp, height = 70.dp)) {
        val max = maxOf(left, right, 1L).toFloat()
        val bw = size.width * 0.38f
        fun h(v: Long) = size.height * (v / max)
        drawRoundRect(
            color = leftColor,
            topLeft = Offset(size.width * 0.05f, size.height - h(left)),
            size = Size(bw, h(left)),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = rightColor.copy(alpha = 0.35f),
            topLeft = Offset(size.width * 0.57f, size.height - h(right)),
            size = Size(bw, h(right)),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = rightColor,
            topLeft = Offset(size.width * 0.57f, size.height - h(rightPart)),
            size = Size(bw, h(rightPart)),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
}

/** Kâr ve nakit akışı blokları (Genel durum görünümü). */
@Composable
fun FlowPanels(f: FlowTotals) {
    val profit = f.income - f.expense
    val cash = f.received - f.paidOut
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("KÂR", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(money(profit), color = if (profit >= 0) BlueMain else RedMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("GELİR", color = BlueMain, fontSize = 11.sp)
                Text(money(f.income), color = BlueMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (f.plannedIncome > 0) BellLine(f.plannedIncome, BlueMain)
            }
            Spacer(Modifier.width(12.dp))
            FlowBars(f.income, f.expense, f.expense - f.unpaidExpense, BlueMain, RedMain)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GİDER", color = RedMain, fontSize = 11.sp)
                Text(money(f.expense), color = RedMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (f.unpaidExpense > 0) BellLine(f.unpaidExpense, RedMain)
            }
        }
        Text(
            "Ödemesi yapılmış ya da yapılmamış tüm gelir ve giderler. Zil: bekleyen tutar (kartla/borçla yapılan veya ileri tarihli).",
            fontSize = 11.sp,
            color = Color.Gray
        )
        HorizontalDivider(color = Color(0xFFDDDDDD))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NAKİT AKIŞI", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(money(cash), color = if (cash >= 0) BlueMain else RedMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("ALINAN", color = BlueMain, fontSize = 11.sp)
                Text(money(f.received), color = BlueMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (f.plannedReceived > 0) BellLine(f.plannedReceived, BlueMain)
            }
            Spacer(Modifier.width(12.dp))
            FlowBars(f.received, f.paidOut, f.paidOut, BlueMain, RedMain)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ÖDENEN", color = RedMain, fontSize = 11.sp)
                Text(money(f.paidOut), color = RedMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (f.plannedPaid > 0) BellLine(f.plannedPaid, RedMain)
            }
        }
        Text("Nakit, banka ve birikim hesapları üzerinden alınan ve verilen paralar", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun SummaryTab(f: FlowTotals) {
    WhiteBox { FlowPanels(f) }
}

/** Ana sayfadaki genel durum kartı: Yıllık / Aylık / Haftalık / Günlük. */
@Composable
fun StatusCard(accounts: List<Account>, txns: List<Txn>) {
    var span by remember { mutableStateOf(Span.MONTH) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    val range = spanRange(span, anchor)
    val f = remember(accounts, txns, range) { flowTotals(accounts, txns, range) }
    val tint = Color(0xFFEAF2FA)
    val usage = if (f.income > 0) (f.expense.toFloat() / f.income * 100f).roundToInt() else null

    WhiteBox {
        Row(Modifier.fillMaxWidth().background(tint), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                Modifier.clickable { anchor = shiftAnchor(span, anchor, -1) }.padding(horizontal = 18.dp, vertical = 8.dp),
                color = BlueMain,
                fontSize = 24.sp
            )
            Text(
                statusTitle(span, anchor),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = BlueMain,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                "›",
                Modifier.clickable { anchor = shiftAnchor(span, anchor, 1) }.padding(horizontal = 18.dp, vertical = 8.dp),
                color = BlueMain,
                fontSize = 24.sp
            )
        }
        Row(Modifier.fillMaxWidth().background(tint).padding(horizontal = 10.dp, vertical = 6.dp)) {
            listOf(Span.YEAR, Span.MONTH, Span.WEEK, Span.DAY).forEach { sp ->
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .background(if (span == sp) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { span = sp }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sp.label,
                        color = if (span == sp) BlueMain else Color(0xFF333333),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
        FlowPanels(f)
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF1F1F1)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("KART HARCAMASI", fontSize = 10.sp, color = Color.Gray)
                Text(money(f.cardSpend), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("KART/KREDİ ÖDEMESİ", fontSize = 10.sp, color = Color.Gray)
                Text(money(f.debtPayments), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("GİDER / GELİR", fontSize = 10.sp, color = Color.Gray)
                Text(if (usage != null) "%$usage" else "—", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChartScreen(
    type: AccountType,
    group: String?,
    accounts: List<Account>,
    txns: List<Txn>,
    month: YearMonth,
    startPie: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val periodic = type == AccountType.INCOME || type == AccountType.EXPENSE
    var tab by remember { mutableStateOf(if (type == AccountType.INCOME) 2 else 1) }
    var span by remember { mutableStateOf(Span.MONTH) }
    var anchor by remember {
        mutableStateOf(if (month == YearMonth.now()) LocalDate.now() else month.atDay(1))
    }
    var pie by remember { mutableStateOf(startPie) }
    var drill by remember { mutableStateOf(group) }
    var balMonth by remember { mutableStateOf(month) }
    var selected by remember(tab, span, anchor, drill) { mutableStateOf<String?>(null) }
    val accent = Color(0xFF1E8A5E)

    BackHandler(enabled = group == null && drill != null) { drill = null }

    val range = spanRange(span, anchor)
    val prevRange = if (span != Span.ALL) spanRange(span, shiftAnchor(span, anchor, -1)) else null
    val shownType = if (!periodic) type else if (tab == 2) AccountType.INCOME else AccountType.EXPENSE
    val amounts = remember(accounts, txns, range, periodic, balMonth) {
        if (periodic) periodTotals(accounts, txns, range) else balances(accounts, txns, balMonth)
    }
    val prevAmounts = remember(accounts, txns, prevRange, periodic) {
        if (periodic && prevRange != null) periodTotals(accounts, txns, prevRange) else emptyMap()
    }
    val ofType = accounts.filter { it.type == shownType }

    fun build(src: Map<Long, Long>): List<Slice> {
        val roll = rollupBalances(accounts, src)
        val raw: List<Slice> = if (drill == null) {
            ofType.groupBy { it.groupName }.map { (g, l) -> Slice(g, l.sumOf { src[it.id] ?: 0L }, g) }
        } else {
            ofType.filter { it.groupName == drill && it.parentId == null }.map { Slice(it.name, roll[it.id] ?: 0L, null) }
        }
        return raw.map { it.copy(cents = abs(it.cents)) }.filter { it.cents > 0L }.sortedByDescending { it.cents }
    }

    val shown = build(amounts)
    val prevMap = build(prevAmounts).associate { it.name to it.cents }
    val total = shown.sumOf { it.cents }
    val merged = shown.size > 8
    val chartSlices = if (merged) {
        shown.take(7) + Slice("Diğer (" + (shown.size - 7) + ")", shown.drop(7).sumOf { it.cents })
    } else shown
    val totalColor = if (shownType == AccountType.INCOME) Color(0xFF1E8A5E) else Color(0xFFC62828)
    val today = LocalDate.now().toEpochDay()
    val endEff = minOf(range.second, today)
    val days = if (span == Span.ALL) 0L else maxOf(1L, endEff - range.first + 1)

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(group ?: if (periodic) "Kategori" else "${type.label} dağılımı", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (periodic && group == null) {
                    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp))) {
                        listOf("ÖZET", "GİDER", "GELİR").forEachIndexed { i, t ->
                            Column(
                                Modifier.weight(1f).clickable { tab = i; drill = null },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    t,
                                    Modifier.padding(vertical = 12.dp),
                                    color = if (tab == i) accent else Color(0xFF222222),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Box(Modifier.height(3.dp).fillMaxWidth().background(if (tab == i) accent else Color.Transparent))
                            }
                        }
                    }
                }
                if (periodic) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Span.values().forEach { sp ->
                            FilterChip(
                                selected = span == sp,
                                onClick = { span = sp },
                                label = { Text(sp.label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().background(accent, RoundedCornerShape(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "‹",
                            Modifier.clickable(enabled = span != Span.ALL) { anchor = shiftAnchor(span, anchor, -1) }
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 24.sp
                        )
                        Text(
                            rangeLabel(span, range),
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            "›",
                            Modifier.clickable(enabled = span != Span.ALL) { anchor = shiftAnchor(span, anchor, 1) }
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                } else {
                    PeriodPill(balMonth) { balMonth = it }
                }

                if (periodic && group == null && tab == 0) {
                    SummaryTab(flowTotals(accounts, txns, range))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = pie, onClick = { pie = true }, label = { Text("Halka") })
                        FilterChip(selected = !pie, onClick = { pie = false }, label = { Text("Çubuk") })
                        if (group == null && drill != null) {
                            Text(
                                "‹ " + drill,
                                Modifier.clickable { drill = null }.padding(8.dp),
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        WhiteBox { Text("Bu aralıkta gösterilecek veri yok.", Modifier.padding(16.dp), color = Color.Gray) }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Toplam", money(total), totalColor, Color.White, Modifier.weight(1f))
                            StatBox(
                                if (days > 0) "Günlük ortalama" else "Kalem sayısı",
                                if (days > 0) money(total / days) else shown.size.toString(),
                                Color(0xFF333333), Color.White, Modifier.weight(1f)
                            )
                            StatBox("En yüksek", shown.first().name, Color(0xFF333333), Color.White, Modifier.weight(1f))
                        }
                        WhiteBox {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                if (pie) {
                                    DonutChart(chartSlices, totalColor, selected) { selected = it }
                                } else {
                                    PastelBars(chartSlices, selected)
                                }
                            }
                        }
                        WhiteBox {
                            Row(
                                Modifier.fillMaxWidth().background(Color(0xFFEFEFEF)).padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Kategori", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Miktar", Modifier.width(96.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("%", Modifier.width(64.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            shown.forEachIndexed { i, s ->
                                val chartIdx = minOf(i, chartSlices.size - 1)
                                val chartName = chartSlices[chartIdx].name
                                val isSel = selected != null && selected == chartName
                                val canDrill = group == null && drill == null && s.drillGroup != null
                                val prev = prevMap[s.name]
                                val change: String? = if (span == Span.ALL || !periodic) null
                                else if (prev != null && prev > 0L) {
                                    val pct = (s.cents - prev) * 100.0 / prev
                                    (if (pct >= 0) "▲ %" else "▼ %") + abs(pct).roundToInt() + " (önceki dönem)"
                                } else "Önceki dönemde yok"
                                val up = prev != null && prev > 0L && s.cents >= prev
                                val good = if (shownType == AccountType.INCOME) up else !up
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (isSel) Color(0x221E8A5E) else Color.Transparent)
                                        .clickable { selected = if (isSel) null else chartName }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(12.dp).background(PASTEL[chartIdx % PASTEL.size], CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (change != null) {
                                            Text(
                                                change,
                                                fontSize = 10.sp,
                                                color = if (prev == null || prev <= 0L) Color.Gray else if (good) Color(0xFF1E8A5E) else Color(0xFFB3261E)
                                            )
                                        }
                                    }
                                    Text(money(s.cents), Modifier.width(96.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                                    Text(
                                        String.format(TR, "%.2f%%", s.cents * 100.0 / total),
                                        Modifier.width(64.dp),
                                        textAlign = TextAlign.End,
                                        fontSize = 12.sp
                                    )
                                    if (canDrill) {
                                        Text(
                                            "›",
                                            Modifier.clickable { drill = s.drillGroup }.padding(start = 10.dp, end = 2.dp),
                                            color = accent,
                                            fontSize = 22.sp
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFE3E3E3))
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("Toplam", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(money(total), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = totalColor)
                            }
                        }
                        Text(
                            if (group == null && drill == null) "Satıra dokununca grafikte vurgulanır, › ile alt kalemlere inersin."
                            else "Satıra dokununca grafikte vurgulanır.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
