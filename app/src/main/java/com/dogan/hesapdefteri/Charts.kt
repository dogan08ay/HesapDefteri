@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val PASTEL = listOf(
    Color(0xFFC5F982), Color(0xFFFFF48D), Color(0xFFFFD08A), Color(0xFF8EE3FF),
    Color(0xFFFF8FA3), Color(0xFFB9A7FF), Color(0xFFA8E6CF), Color(0xFFFFB3D9)
)

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

data class FlowTotals(val income: Long, val expense: Long, val credit: Long, val received: Long, val paidOut: Long)

fun flowTotals(accounts: List<Account>, txns: List<Txn>, r: Pair<Long, Long>): FlowTotals {
    val byId = accounts.associateBy { it.id }
    var income = 0L
    var expense = 0L
    var credit = 0L
    var received = 0L
    var paidOut = 0L
    for (t in txns) {
        if (t.date < r.first || t.date > r.second) continue
        val f = byId[t.fromId]?.type
        val to = byId[t.toId]?.type
        if (f == AccountType.INCOME) {
            income += t.cents
            if (to == AccountType.ASSET) received += t.cents
        }
        if (to == AccountType.EXPENSE) {
            expense += t.cents
            if (f == AccountType.LIABILITY) credit += t.cents
            if (f == AccountType.ASSET) paidOut += t.cents
        }
        if (f == AccountType.ASSET && to == AccountType.LIABILITY) paidOut += t.cents
    }
    return FlowTotals(income, expense, credit, received, paidOut)
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
fun DonutChart(slices: List<Slice>, totalColor: Color) {
    val total = slices.sumOf { it.cents }.toFloat()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 320.dp)
        Box(Modifier.size(side)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = s.cents / total * 360f
                    drawArc(color = PASTEL[i % PASTEL.size], startAngle = start, sweepAngle = sweep, useCenter = true)
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
            }
            val half = side.value / 2f
            var acc = -90f
            slices.forEachIndexed { i, s ->
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
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Toplam", fontSize = 12.sp, color = totalColor)
                Text(money(slices.sumOf { it.cents }), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = totalColor)
            }
        }
    }
}

@Composable
fun PastelBars(slices: List<Slice>) {
    val max = slices.maxOf { it.cents }.toFloat()
    Canvas(Modifier.size(width = 300.dp, height = 220.dp)) {
        val bw = size.width / (slices.size * 2f)
        slices.forEachIndexed { i, s ->
            val h = s.cents / max * size.height
            drawRoundRect(
                color = PASTEL[i % PASTEL.size],
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

@Composable
fun SummaryTab(f: FlowTotals) {
    val blue = Color(0xFF1565C0)
    val red = Color(0xFFC62828)
    val profit = f.income - f.expense
    val cash = f.received - f.paidOut
    WhiteBox {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KÂR", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(money(profit), color = if (profit >= 0) blue else red, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("GELİR", color = blue, fontSize = 11.sp)
                    Text(money(f.income), color = blue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(12.dp))
                FlowBars(f.income, f.expense, f.expense - f.credit, blue, red)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("GİDER", color = red, fontSize = 11.sp)
                    Text(money(f.expense), color = red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (f.credit > 0) Text("Ödenmemiş: " + money(f.credit), color = red, fontSize = 11.sp)
                }
            }
            Text("Ödemesi yapılmış ya da yapılmamış tüm gelir ve giderler", fontSize = 11.sp, color = Color.Gray)
        }
    }
    WhiteBox {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NAKİT AKIŞI", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(money(cash), color = if (cash >= 0) blue else red, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("ALINAN", color = blue, fontSize = 11.sp)
                    Text(money(f.received), color = blue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(12.dp))
                FlowBars(f.received, f.paidOut, f.paidOut, blue, red)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ÖDENEN", color = red, fontSize = 11.sp)
                    Text(money(f.paidOut), color = red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Text("Nakit, banka ve birikim hesapları üzerinden alınan ve verilen paralar", fontSize = 11.sp, color = Color.Gray)
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
    var anchor by remember { mutableStateOf(month.atDay(1)) }
    var pie by remember { mutableStateOf(startPie) }
    var drill by remember { mutableStateOf(group) }
    var balMonth by remember { mutableStateOf(month) }
    val accent = Color(0xFF1E8A5E)

    BackHandler(enabled = group == null && drill != null) { drill = null }

    val range = spanRange(span, anchor)
    val shownType = if (!periodic) type else if (tab == 2) AccountType.INCOME else AccountType.EXPENSE
    val amounts = remember(accounts, txns, range, periodic, balMonth) {
        if (periodic) periodTotals(accounts, txns, range) else balances(accounts, txns, balMonth)
    }
    val roll = remember(accounts, amounts) { rollupBalances(accounts, amounts) }
    val ofType = accounts.filter { it.type == shownType }
    val raw: List<Slice> = if (drill == null) {
        ofType.groupBy { it.groupName }.map { (g, l) -> Slice(g, l.sumOf { amounts[it.id] ?: 0L }, g) }
    } else {
        ofType.filter { it.groupName == drill && it.parentId == null }.map { Slice(it.name, roll[it.id] ?: 0L, null) }
    }
    val shown = raw.map { it.copy(cents = abs(it.cents)) }.filter { it.cents > 0L }.sortedByDescending { it.cents }
    val total = shown.sumOf { it.cents }
    val totalColor = if (shownType == AccountType.INCOME) Color(0xFF1E8A5E) else Color(0xFFC62828)

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
                                "‹ ${drill}",
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
                        WhiteBox {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                if (pie) DonutChart(shown, totalColor) else PastelBars(shown)
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
                                val canDrill = group == null && drill == null && s.drillGroup != null
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = canDrill) { drill = s.drillGroup }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(12.dp).background(PASTEL[i % PASTEL.size], CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(s.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(money(s.cents), Modifier.width(96.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                                    Text(
                                        String.format(TR, "%.2f%%", s.cents * 100.0 / total),
                                        Modifier.width(64.dp),
                                        textAlign = TextAlign.End,
                                        fontSize = 12.sp
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFE3E3E3))
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("Toplam", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(money(total), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = totalColor)
                            }
                        }
                        if (group == null && drill == null) {
                            Text("Alt kalemleri görmek için bir kategoriye dokun.", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
