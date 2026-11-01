@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TR: Locale = Locale.forLanguageTag("tr-TR")

fun money(cents: Long): String = String.format(TR, "%,.2f ₺", cents / 100.0)

fun dayText(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

sealed interface Screen {
    data object Home : Screen
    data class Category(val type: AccountType) : Screen
    data class AddTxn(val type: AccountType) : Screen
    data class AddAccount(val type: AccountType) : Screen
}

/**
 * Hesap bakiyeleri. Varlık ve Gider hesapları hedef olunca artar, kaynak olunca azalır;
 * Borç ve Gelir hesapları tersi. Gelir/Gider seçili dönemle, Varlık/Borç tüm zamanla hesaplanır.
 */
fun balances(accounts: List<Account>, txns: List<Txn>, month: YearMonth): Map<Long, Long> {
    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    val byId = accounts.associateBy { it.id }
    val res = HashMap<Long, Long>()

    fun apply(acc: Account?, date: Long, cents: Long, isTarget: Boolean) {
        if (acc == null) return
        val periodic = acc.type == AccountType.INCOME || acc.type == AccountType.EXPENSE
        if (periodic && (date < start || date > end)) return
        val plusOnTarget = acc.type == AccountType.ASSET || acc.type == AccountType.EXPENSE
        val delta = if (isTarget == plusOnTarget) cents else -cents
        res[acc.id] = (res[acc.id] ?: 0L) + delta
    }

    for (t in txns) {
        apply(byId[t.fromId], t.date, t.cents, isTarget = false)
        apply(byId[t.toId], t.date, t.cents, isTarget = true)
    }
    return res
}

@Composable
fun App(vm: MainVm) {
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (val s = screen) {
            is Screen.AddTxn -> Screen.Category(s.type)
            is Screen.AddAccount -> Screen.Category(s.type)
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            accounts, txns, month,
            onMonth = { month = it },
            onOpen = { screen = Screen.Category(it) },
            onAdd = { screen = Screen.AddTxn(it) }
        )
        is Screen.Category -> CategoryScreen(
            s.type, accounts, txns, month,
            onMonth = { month = it },
            onBack = { screen = Screen.Home },
            onAddTxn = { screen = Screen.AddTxn(s.type) },
            onAddAccount = { screen = Screen.AddAccount(s.type) },
            onDelete = { vm.deleteTxn(it) }
        )
        is Screen.AddTxn -> AddTxnScreen(
            s.type, accounts,
            onBack = { screen = Screen.Category(s.type) },
            onSave = { cents, date, note, from, to ->
                vm.addTxn(cents, date, note, from, to)
                screen = Screen.Category(s.type)
            }
        )
        is Screen.AddAccount -> AddAccountScreen(
            s.type,
            onBack = { screen = Screen.Category(s.type) },
            onSave = { name, group ->
                vm.addAccount(name, group, s.type)
                screen = Screen.Category(s.type)
            }
        )
    }
}

@Composable
fun MonthBar(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹") }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", TR)),
            style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text("›") }
    }
}

@Composable
fun HomeScreen(
    accounts: List<Account>,
    txns: List<Txn>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onOpen: (AccountType) -> Unit,
    onAdd: (AccountType) -> Unit
) {
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    Scaffold(topBar = { TopAppBar(title = { Text("Hesap Defteri") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MonthBar(month, onMonth)
            AccountType.values().forEach { type ->
                val total = accounts.filter { it.type == type }.sumOf { bal[it.id] ?: 0L }
                Card(onClick = { onOpen(type) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(type.label, style = MaterialTheme.typography.titleMedium)
                            Text(money(total), style = MaterialTheme.typography.headlineSmall)
                        }
                        FilledIconButton(onClick = { onAdd(type) }) {
                            Icon(Icons.Default.Add, contentDescription = "İşlem ekle")
                        }
                    }
                }
            }
            DailyCard(accounts, txns)
        }
    }
}

@Composable
fun CategoryScreen(
    type: AccountType,
    accounts: List<Account>,
    txns: List<Txn>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onAddTxn: () -> Unit,
    onAddAccount: () -> Unit,
    onDelete: (Txn) -> Unit
) {
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val mine = accounts.filter { it.type == type }
    val periodic = type == AccountType.INCOME || type == AccountType.EXPENSE
    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    val list = txns.filter { t ->
        val touches = byId[t.fromId]?.type == type || byId[t.toId]?.type == type
        touches && (!periodic || (t.date in start..end))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { MonthBar(month, onMonth) }
            item {
                Text(
                    money(mine.sumOf { bal[it.id] ?: 0L }),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddTxn) { Text("Yeni işlem") }
                    OutlinedButton(onClick = onAddAccount) { Text("Yeni hesap") }
                }
            }
            item { Text("Hesaplar", style = MaterialTheme.typography.titleMedium) }
            mine.groupBy { it.groupName }.forEach { (group, accs) ->
                item {
                    Text(
                        "$group — ${money(accs.sumOf { bal[it.id] ?: 0L })}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                items(accs, key = { "a${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(a.name)
                        Text(money(bal[a.id] ?: 0L))
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("İşlemler", style = MaterialTheme.typography.titleMedium) }
            if (list.isEmpty()) item { Text("Bu dönemde işlem yok.") }
            items(list, key = { "t${it.id}" }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${byId[t.fromId]?.name ?: "?"} → ${byId[t.toId]?.name ?: "?"}")
                            Text(
                                dayText(t.date) + if (t.note.isNotBlank()) " · ${t.note}" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(money(t.cents))
                        IconButton(onClick = { onDelete(t) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil")
                        }
                    }
                }
            }
        }
    }
}

enum class TxMode(val label: String, val from: AccountType, val to: AccountType) {
    INCOME_IN("Gelir", AccountType.INCOME, AccountType.ASSET),
    EXPENSE_CASH("Varlıktan öde", AccountType.ASSET, AccountType.EXPENSE),
    EXPENSE_DEBT("Borçla öde", AccountType.LIABILITY, AccountType.EXPENSE),
    TRANSFER("Hesaplar arası", AccountType.ASSET, AccountType.ASSET),
    BORROW("Borç aldım", AccountType.LIABILITY, AccountType.ASSET),
    REPAY("Borç ödedim", AccountType.ASSET, AccountType.LIABILITY)
}

fun modesFor(type: AccountType): List<TxMode> = when (type) {
    AccountType.INCOME -> listOf(TxMode.INCOME_IN)
    AccountType.EXPENSE -> listOf(TxMode.EXPENSE_CASH, TxMode.EXPENSE_DEBT)
    AccountType.ASSET -> listOf(TxMode.TRANSFER)
    AccountType.LIABILITY -> listOf(TxMode.BORROW, TxMode.REPAY)
}

@Composable
fun AccountPicker(label: String, options: List<Account>, selected: Account?, onSelect: (Account) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.name ?: "seç"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { a ->
                DropdownMenuItem(
                    text = { Text("${a.name} (${a.groupName})") },
                    onClick = { onSelect(a); open = false }
                )
            }
        }
    }
}

@Composable
fun AddTxnScreen(
    type: AccountType,
    accounts: List<Account>,
    onBack: () -> Unit,
    onSave: (cents: Long, date: Long, note: String, fromId: Long, toId: Long) -> Unit
) {
    val modes = modesFor(type)
    var mode by remember { mutableStateOf(modes.first()) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDate by remember { mutableStateOf(false) }

    val fromOpts = accounts.filter { it.type == mode.from }
    val toOpts = accounts.filter { it.type == mode.to }
    var from by remember(mode) { mutableStateOf(fromOpts.firstOrNull()) }
    var to by remember(mode) {
        mutableStateOf(if (mode.from == mode.to) toOpts.getOrNull(1) ?: toOpts.firstOrNull() else toOpts.firstOrNull())
    }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni işlem · ${type.label}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (modes.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { m ->
                        FilterChip(selected = m == mode, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Tutar (₺)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Tarih: ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
            }
            AccountPicker("Kaynak hesap", fromOpts, from) { from = it }
            AccountPicker("Hedef hesap", toOpts, to) { to = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Açıklama (isteğe bağlı)") },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val value = amountText.trim().replace(',', '.').toBigDecimalOrNull()
                    val cents = value?.multiply(BigDecimal(100))?.toLong() ?: 0L
                    val f = from
                    val t = to
                    when {
                        cents <= 0L -> error = "Geçerli bir tutar girin."
                        f == null || t == null -> error = "Kaynak ve hedef hesap seçin."
                        f.id == t.id -> error = "Kaynak ve hedef farklı olmalı."
                        else -> onSave(cents, date.toEpochDay(), note.trim(), f.id, t.id)
                    }
                }
            ) { Text("Kaydet") }
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("Tamam") }
            }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun AddAccountScreen(type: AccountType, onBack: () -> Unit, onSave: (name: String, group: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni hesap · ${type.label}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Hesap adı") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = group, onValueChange = { group = it },
                label = { Text("Alt kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), group.trim().ifEmpty { "Genel" }) }
            ) { Text("Kaydet") }
        }
    }
}


@Composable
fun DailyCard(accounts: List<Account>, txns: List<Txn>) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val dayTxns = remember(txns, day) { txns.filter { it.date == day.toEpochDay() } }
    val income = dayTxns.filter { byId[it.fromId]?.type == AccountType.INCOME }.sumOf { it.cents }
    val expense = dayTxns.filter { byId[it.toId]?.type == AccountType.EXPENSE }.sumOf { it.cents }
    val title = if (day == LocalDate.now()) "Bugün" else day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", TR))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { day = day.minusDays(1) }) { Text("‹") }
                Text("Günlük işlemler · $title", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { day = day.plusDays(1) }) { Text("›") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gelir: ${money(income)}")
                Text("Gider: ${money(expense)}")
            }
            Text("Net: ${money(income - expense)}", style = MaterialTheme.typography.titleSmall)
            if (dayTxns.isEmpty()) {
                Text("Bu günde işlem yok.", style = MaterialTheme.typography.bodySmall)
            }
            dayTxns.forEach { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${byId[t.fromId]?.name ?: "?"} → ${byId[t.toId]?.name ?: "?"}")
                        if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(t.cents))
                }
            }
        }
    }
}
