package com.dogan.hesapdefteri

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * "1.250,50", "1250,5", "1250.50", "1.250.000" gibi girişleri kuruşa çevirir.
 * Virgül varsa Türkçe biçim (nokta binlik), yalnız nokta varsa ve birden fazlaysa binlik ayracı sayılır.
 */
fun parseCents(text: String): Long? {
    var t = text.trim().replace(" ", "").replace("TL", "", ignoreCase = true)
    if (t.isEmpty()) return null
    t = if (t.contains(',')) {
        t.replace(".", "").replace(',', '.')
    } else if (t.count { it == '.' } > 1) {
        t.replace(".", "")
    } else t
    return t.toBigDecimalOrNull()?.multiply(BigDecimal(100))?.setScale(0, RoundingMode.HALF_UP)?.toLong()
}

fun clampDay(ym: YearMonth, day: Int): LocalDate = ym.atDay(minOf(day, ym.lengthOfMonth()))

/** Verilen tarihe kadar gelen en son hesap kesim tarihi (o gün dahil). */
fun lastCutoff(day: Int, ref: LocalDate): LocalDate {
    val thisMonth = clampDay(YearMonth.from(ref), day)
    return if (!thisMonth.isAfter(ref)) thisMonth else clampDay(YearMonth.from(ref).minusMonths(1), day)
}

data class CardInfo(
    val limit: Long?,
    val remaining: Long?,
    val debtToday: Long,
    val futureCharges: Long,
    val cutoffDay: Int?,
    val lastCutoff: LocalDate?,
    val prevCutoff: LocalDate?,
    val nextCutoff: LocalDate?,
    val statementDebt: Long,
    val periodSpend: Long
)

/**
 * Banka mantığı: hesap kesim gününde o güne kadarki borç ekstre borcu olur.
 * Kesimden sonraki harcamalar güncel borca eklenir ama ekstreye girmez.
 * Kesimden sonra yapılan ödemeler önce ekstre borcunu düşürür.
 */
fun cardInfo(acc: Account, accounts: List<Account>, txns: List<Txn>, today: LocalDate = LocalDate.now()): CardInfo {
    val byId = accounts.associateBy { it.id }
    val ids = subtreeIds(accounts, acc.id)
    val todayDay = today.toEpochDay()
    val day = acc.cutoffDay
    val last = if (day != null) lastCutoff(day, today) else null
    val prev = if (day != null && last != null) clampDay(YearMonth.from(last).minusMonths(1), day) else null
    val next = if (day != null && last != null) clampDay(YearMonth.from(last).plusMonths(1), day) else null
    val lastDay = last?.toEpochDay()

    var debtToday = 0L
    var future = 0L
    var debtAtL = 0L
    var payAfter = 0L
    var chargeAfter = 0L
    for (t in txns) {
        val d = txnDelta(t, byId, ids)
        if (d == 0L) continue
        if (t.date > todayDay) {
            if (d > 0) future += d
            continue
        }
        debtToday += d
        if (lastDay != null) {
            if (t.date <= lastDay) {
                debtAtL += d
            } else if (d < 0) {
                payAfter += -d
            } else {
                chargeAfter += d
            }
        }
    }
    var statement = 0L
    var period = 0L
    if (lastDay != null) {
        statement = maxOf(0L, debtAtL - payAfter)
        val over = maxOf(0L, payAfter - maxOf(debtAtL, 0L))
        period = maxOf(0L, chargeAfter - over)
    }
    val limit = acc.limitCents
    val remaining = if (limit != null) limit - debtToday - future else null
    return CardInfo(limit, remaining, debtToday, future, day, last, prev, next, statement, period)
}

/** Tohumlanan genel hesaplar. Gerçek bir hesap eklenince ilgili grupta gereksiz kalırlar. */
val PLACEHOLDERS: Map<String, String> = mapOf(
    "Banka hesapları" to "Vadesiz hesap",
    "Kredi kartları" to "Kredi kartı",
    "Krediler" to "Banka kredisi",
    "Özel borçlar" to "Kişisel borç",
    "Fatura borçları" to "Fatura borcu",
    "Alacaklar" to "Alacak",
    "Yatırım fonları" to "Fon",
    "Hisse senetleri" to "Hisse portföyü",
    "Diğer varlıklar" to "Diğer varlık"
)

fun isPlaceholder(a: Account): Boolean =
    a.parentId == null &&
        (a.type == AccountType.ASSET || a.type == AccountType.LIABILITY) &&
        PLACEHOLDERS[a.groupName] == a.name

/** Aynı grupta gerçek (adı verilmiş) bir hesap varken artık kalan varsayılan hesaplar. */
fun supersededPlaceholders(accounts: List<Account>): List<Account> {
    val realGroups = accounts.filter { !isPlaceholder(it) }.map { it.type to it.groupName }.toSet()
    return accounts.filter { isPlaceholder(it) && (it.type to it.groupName) in realGroups }
}

/** İşlem girerken seçilebilecek hesaplar: gereksiz kalan varsayılan hesaplar gizlenir. */
fun selectable(accounts: List<Account>): List<Account> {
    val hide = supersededPlaceholders(accounts).map { it.id }.toSet()
    return accounts.filter { it.id !in hide }
}
