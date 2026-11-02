@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import android.app.Activity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

sealed interface Screen {
    data object Home : Screen
    data class Category(val type: AccountType) : Screen
    data class Group(val type: AccountType, val group: String) : Screen
    data class AccountDetail(val type: AccountType, val group: String, val accountId: Long) : Screen
    data class Chart(val type: AccountType, val group: String?, val pie: Boolean) : Screen
    data class TxTypes(val type: AccountType, val group: String?, val accountId: Long?, val back: Screen) : Screen
    data class AddTxn(
        val type: AccountType,
        val fav: Favorite?,
        val preset: Preset?,
        val accountId: Long?,
        val back: Screen
    ) : Screen
    data class AddAccount(val type: AccountType, val group: String, val back: Screen) : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data object PinSetup : Screen
    data class Info(val title: String, val body: String) : Screen
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

fun groupTotals(accounts: List<Account>, type: AccountType, bal: Map<Long, Long>): List<Pair<String, Long>> =
    accounts.filter { it.type == type }
        .groupBy { it.groupName }
        .map { (g, list) -> g to list.sumOf { bal[it.id] ?: 0L } }

fun favType(accounts: List<Account>, f: Favorite): AccountType {
    val fromT = accounts.firstOrNull { it.id == f.fromId }?.type
    val toT = accounts.firstOrNull { it.id == f.toId }?.type
    return when {
        fromT == AccountType.INCOME -> AccountType.INCOME
        toT == AccountType.EXPENSE -> AccountType.EXPENSE
        fromT == AccountType.LIABILITY || toT == AccountType.LIABILITY -> AccountType.LIABILITY
        else -> AccountType.ASSET
    }
}

fun presetOptions(accounts: List<Account>, type: AccountType, group: String?, accountId: Long?): List<Preset> {
    val all = optionsFor(type, group)
    val acc = accountId?.let { id -> accounts.firstOrNull { it.id == id } } ?: return all
    return all.filter { it.mode.from == acc.type || it.mode.to == acc.type }.ifEmpty { all }
}

@Composable
fun App(vm: MainVm) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var unlocked by remember { mutableStateOf(!PinStore.isSet(ctx)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    fun goBack() {
        screen = when (val s = screen) {
            Screen.Home -> Screen.Home
            is Screen.Category -> Screen.Home
            is Screen.Group -> Screen.Category(s.type)
            is Screen.AccountDetail -> Screen.Group(s.type, s.group)
            is Screen.Chart -> if (s.group != null) Screen.Group(s.type, s.group) else Screen.Category(s.type)
            is Screen.TxTypes -> s.back
            is Screen.AddTxn -> s.back
            is Screen.AddAccount -> s.back
            Screen.Favorites, Screen.Settings, Screen.PinSetup, is Screen.Info -> Screen.Home
        }
    }

    fun openNewTx(type: AccountType, group: String?, back: Screen, accountId: Long? = null) {
        val opts = presetOptions(accounts, type, group, accountId)
        screen = if (opts.size == 1) {
            Screen.AddTxn(type, null, opts[0], accountId, back)
        } else {
            Screen.TxTypes(type, group, accountId, back)
        }
    }

    BackHandler(enabled = unlocked && screen != Screen.Home) { goBack() }

    if (!unlocked) {
        LockScreen { unlocked = true }
        return
    }

    val activity = ctx as? Activity
    val menu: List<Pair<String, () -> Unit>> = listOf(
        "Ayarlar" to { screen = Screen.Settings },
        "Uygulama kilidi (PIN)" to { screen = Screen.PinSetup },
        "Kullanım kılavuzu" to { screen = Screen.Info("Kullanım kılavuzu", GUIDE) },
        "İpuçları" to { screen = Screen.Info("İpuçları", TIPS) },
        "Gizlilik politikası" to { screen = Screen.Info("Gizlilik politikası", PRIVACY) },
        "Hakkında ve yenilikler" to { screen = Screen.Info("Hakkında", ABOUT) },
        "Çıkış" to { activity?.finish() }
    )

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            accounts, txns, month,
            onMonth = { month = it },
            onOpen = { screen = Screen.Category(it) },
            onAdd = { openNewTx(it, null, Screen.Home) },
            onOpenFavs = { screen = Screen.Favorites },
            menu = menu
        )
        is Screen.Category -> CategoryScreen(
            s.type, accounts, txns, month,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onAddTxn = { openNewTx(s.type, null, s) },
            onOpenGroup = { screen = Screen.Group(s.type, it) },
            onChart = { screen = Screen.Chart(s.type, null, it) },
            onAddAccount = { screen = Screen.AddAccount(s.type, "", s) }
        )
        is Screen.Group -> GroupScreen(
            s.type, s.group, accounts, txns, month,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onAddTxn = { openNewTx(s.type, s.group, s) },
            onAddAccount = { screen = Screen.AddAccount(s.type, s.group, s) },
            onChart = { screen = Screen.Chart(s.type, s.group, it) },
            onOpenAccount = { a -> screen = Screen.AccountDetail(s.type, s.group, a.id) },
            onRename = { a, n -> vm.updateAccount(a.copy(name = n)) },
            onDeleteAccount = { vm.deleteAccount(it) },
            onDelete = { vm.deleteTxn(it) }
        )
        is Screen.AccountDetail -> AccountDetailScreen(
            s.type, s.group, s.accountId, accounts, txns, month,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onAddTxn = { openNewTx(s.type, s.group, s, s.accountId) },
            onDelete = { vm.deleteTxn(it) }
        )
        is Screen.Chart -> ChartScreen(
            s.type, s.group, accounts, txns, month, s.pie,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home }
        )
        is Screen.TxTypes -> TxTypesScreen(
            presetOptions(accounts, s.type, s.group, s.accountId),
            onPick = { p -> screen = Screen.AddTxn(s.type, null, p, s.accountId, s) },
            onBack = { goBack() },
            onHome = { screen = Screen.Home }
        )
        is Screen.AddTxn -> {
            val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
            AddTxnScreen(
                s.type, accounts, bal, s.fav, s.preset, s.accountId,
                onBack = { goBack() },
                onHome = { screen = Screen.Home },
                onNewAccount = { name, group, type, done -> vm.addAccount(name, group, type, done) },
                onSave = { list, fav ->
                    vm.addTxns(list, fav)
                    screen = (s.back as? Screen.TxTypes)?.back ?: s.back
                }
            )
        }
        is Screen.AddAccount -> AddAccountScreen(
            s.type, s.group, accounts,
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onSave = { name, group ->
                vm.addAccount(name, group, s.type)
                goBack()
            }
        )
        Screen.Favorites -> FavoritesScreen(
            favorites,
            onPreset = { p -> screen = Screen.AddTxn(typeOfMode(p.mode), null, p, null, Screen.Favorites) },
            onUseFav = { f -> screen = Screen.AddTxn(favType(accounts, f), f, null, null, Screen.Favorites) },
            onDeleteFav = { vm.deleteFavorite(it) },
            onBack = { goBack() },
            onHome = { screen = Screen.Home }
        )
        Screen.Settings -> SettingsScreen(
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onReset = { vm.resetAll() }
        )
        Screen.PinSetup -> PinSetupScreen(onBack = { goBack() }, onHome = { screen = Screen.Home })
        is Screen.Info -> InfoScreen(s.title, s.body, onBack = { goBack() }, onHome = { screen = Screen.Home })
    }
}

@Composable
fun FavoritesEntry(onClick: () -> Unit) {
    WhiteBox {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalcIcon(Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Text("Sık kullanılanlar", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}

@Composable
fun SideMenu(items: List<Pair<String, () -> Unit>>, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onClose))
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF161616))
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { (label, action) ->
                Text(
                    label,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClose(); action() }
                        .padding(horizontal = 18.dp, vertical = 13.dp)
                )
                HorizontalDivider(color = Color(0xFF3A3A3A))
            }
        }
    }
}

@Composable
fun HomeScreen(
    accounts: List<Account>,
    txns: List<Txn>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onOpen: (AccountType) -> Unit,
    onAdd: (AccountType) -> Unit,
    onOpenFavs: () -> Unit,
    menu: List<Pair<String, () -> Unit>>
) {
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    fun total(t: AccountType) = accounts.filter { it.type == t }.sumOf { bal[it.id] ?: 0L }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Hesap Defteri", null, null, onMenu = { menuOpen = true }, logo = true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PeriodPill(month, onMonth)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TypeCard(AccountType.ASSET, total(AccountType.ASSET), { onOpen(AccountType.ASSET) }, { onAdd(AccountType.ASSET) }, Modifier.weight(1f))
                        TypeCard(AccountType.LIABILITY, total(AccountType.LIABILITY), { onOpen(AccountType.LIABILITY) }, { onAdd(AccountType.LIABILITY) }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TypeCard(AccountType.INCOME, total(AccountType.INCOME), { onOpen(AccountType.INCOME) }, { onAdd(AccountType.INCOME) }, Modifier.weight(1f))
                        TypeCard(AccountType.EXPENSE, total(AccountType.EXPENSE), { onOpen(AccountType.EXPENSE) }, { onAdd(AccountType.EXPENSE) }, Modifier.weight(1f))
                    }
                    FavoritesEntry(onOpenFavs)
                    DailyCard(accounts, txns)
                }
                if (menuOpen) SideMenu(menu) { menuOpen = false }
            }
        }
    }
}

@Composable
fun ListButton(title: String, centered: Boolean, onClick: () -> Unit) {
    WhiteBox {
        Text(
            title,
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    onPreset: (Preset) -> Unit,
    onUseFav: (Favorite) -> Unit,
    onDeleteFav: (Favorite) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Sık kullanılanlar", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PRESETS.forEach { p -> ListButton(p.title, true) { onPreset(p) } }
                if (favorites.isNotEmpty()) {
                    Text("Kayıtlı kısayollarım", fontWeight = FontWeight.Bold, color = GreenText, style = MaterialTheme.typography.titleMedium)
                    favorites.forEach { f ->
                        WhiteBox {
                            Row(
                                Modifier.fillMaxWidth().clickable { onUseFav(f) }.padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${f.name} · ${money(f.cents)}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { onDeleteFav(f) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TxTypesScreen(options: List<Preset>, onPick: (Preset) -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Bir işlem tipi seçiniz", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                options.forEach { p -> ListButton(p.title, false) { onPick(p) } }
            }
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
    onHome: () -> Unit,
    onAddTxn: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onChart: (Boolean) -> Unit,
    onAddAccount: () -> Unit
) {
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val groups = remember(accounts, bal, type) { groupTotals(accounts, type, bal) }
    val total = groups.sumOf { it.second }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("${type.label} (${money(total)})", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PeriodPill(month, onMonth)
                WhiteBox {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bakiye: ${money(total)}", Modifier.weight(1f), color = GreenText, style = MaterialTheme.typography.titleMedium)
                        ChartIconButton(true) { onChart(true) }
                        Spacer(Modifier.width(8.dp))
                        ChartIconButton(false) { onChart(false) }
                    }
                }
                Button(
                    onClick = onAddTxn,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = typeColor(type))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Yeni işlem")
                }
                WhiteBox {
                    Text(
                        "${type.label} Kategorileri",
                        Modifier.fillMaxWidth().background(Color(0xFFEFEFEF)).padding(8.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = GreenText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    groups.forEach { (g, v) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenGroup(g) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(g, color = GreenText, fontWeight = FontWeight.Bold)
                            Text(money(v), color = if (v < 0) NegativeRed else Color.DarkGray)
                        }
                        HorizontalDivider()
                    }
                }
                AddPanel("Yeni hesap ekle", typeColor(type), onAddAccount)
            }
        }
    }
}

fun deltaFor(acc: Account?, isTarget: Boolean, cents: Long): Long {
    if (acc == null) return 0L
    val plusOnTarget = acc.type == AccountType.ASSET || acc.type == AccountType.EXPENSE
    return if (isTarget == plusOnTarget) cents else -cents
}

fun txnDelta(t: Txn, byId: Map<Long, Account>, ids: Set<Long>): Long {
    var d = 0L
    if (t.fromId in ids) d += deltaFor(byId[t.fromId], false, t.cents)
    if (t.toId in ids) d += deltaFor(byId[t.toId], true, t.cents)
    return d
}

@Composable
fun Breadcrumb(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(Color(0xFFF1F1F1)).padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = GreenText,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(Color(0xFFEFEFEF)).padding(8.dp),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = GreenText,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun SignBadge(sign: Int) {
    val bg = if (sign > 0) Color(0xFF2E9E62) else if (sign < 0) Color(0xFFC0392B) else Color(0xFF8E8E8E)
    val glyph = if (sign > 0) "+" else if (sign < 0) "−" else "⇄"
    Box(Modifier.size(26.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(glyph, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun TxnLine(title: String, subtitle: String, cents: Long, sign: Int, zebra: Boolean, onDelete: (() -> Unit)?) {
    val color = if (sign > 0) Color(0xFF1E8A5E) else if (sign < 0) Color(0xFFB3261E) else Color(0xFF555555)
    val prefix = if (sign > 0) "+" else if (sign < 0) "−" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (zebra) Color(0xFFF6F6F6) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SignBadge(sign)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(prefix + money(cents), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun TxnTable(
    title: String,
    list: List<Txn>,
    byId: Map<Long, Account>,
    ids: Set<Long>?,
    onDelete: ((Txn) -> Unit)?
) {
    WhiteBox {
        SectionHeader(title)
        if (list.isEmpty()) {
            Text("Bu dönemde işlem yok.", Modifier.padding(14.dp), color = Color.Gray, fontSize = 12.sp)
        }
        list.forEachIndexed { i, t ->
            val delta = if (ids == null) 0L else txnDelta(t, byId, ids)
            val sign = if (delta > 0) 1 else if (delta < 0) -1 else 0
            val sub = dayText(t.date) + if (t.note.isNotBlank()) " · ${t.note}" else ""
            TxnLine(
                "${byId[t.fromId]?.name ?: "?"} → ${byId[t.toId]?.name ?: "?"}",
                sub, t.cents, sign, i % 2 == 1,
                if (onDelete != null) ({ onDelete(t) }) else null
            )
            HorizontalDivider(color = Color(0xFFE3E3E3))
        }
    }
}

@Composable
fun GroupScreen(
    type: AccountType,
    group: String,
    accounts: List<Account>,
    txns: List<Txn>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onAddTxn: () -> Unit,
    onAddAccount: () -> Unit,
    onChart: (Boolean) -> Unit,
    onOpenAccount: (Account) -> Unit,
    onRename: (Account, String) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onDelete: (Txn) -> Unit
) {
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val mine = accounts.filter { it.type == type && it.groupName == group }
    val ids = mine.map { it.id }.toSet()
    val total = mine.sumOf { bal[it.id] ?: 0L }
    val periodic = type == AccountType.INCOME || type == AccountType.EXPENSE
    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    val list = txns.filter { t ->
        (t.fromId in ids || t.toId in ids) && (!periodic || t.date in start..end)
    }
    var editMode by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("$group (${money(total)})", onBack, onHome, onEdit = { editMode = !editMode; message = null })
            Breadcrumb("${type.label} > $group")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PeriodPill(month, onMonth)
                WhiteBox {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bakiye: ${money(total)}", Modifier.weight(1f), color = GreenText, style = MaterialTheme.typography.titleMedium)
                        ChartIconButton(true) { onChart(true) }
                        Spacer(Modifier.width(8.dp))
                        ChartIconButton(false) { onChart(false) }
                    }
                }
                Button(
                    onClick = onAddTxn,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = typeColor(type))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Yeni işlem gir")
                }
                if (editMode) {
                    Text(
                        "Düzenleme açık: bir hesabın adına dokunarak adını değiştir, çöp kutusuyla sil.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                message?.let { Text(it, color = NegativeRed) }
                WhiteBox {
                    SectionHeader("Hesaplar")
                    mine.forEach { a ->
                        val v = bal[a.id] ?: 0L
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (editMode) { renaming = a; renameText = a.name } else onOpenAccount(a) }
                                .padding(horizontal = 14.dp, vertical = if (editMode) 3.dp else 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(a.name, Modifier.weight(1f), color = GreenText, fontWeight = FontWeight.Bold)
                            Text(money(v), color = if (v < 0) NegativeRed else Color.DarkGray)
                            if (editMode) {
                                IconButton(onClick = {
                                    if (txns.any { it.fromId == a.id || it.toId == a.id }) {
                                        message = "“${a.name}” hesabında işlem var, silinemez."
                                    } else {
                                        message = null
                                        onDeleteAccount(a)
                                    }
                                }) { Icon(Icons.Default.Delete, contentDescription = "Hesabı sil") }
                            } else {
                                Text("›", Modifier.padding(start = 8.dp), color = Color.Gray, fontSize = 20.sp)
                            }
                        }
                        HorizontalDivider()
                    }
                    Box(Modifier.padding(10.dp)) {
                        AddPanel("Yeni hesap ekle", typeColor(type), onAddAccount)
                    }
                }
                TxnTable("İşlemler", list, byId, ids) { onDelete(it) }
            }
        }
    }

    renaming?.let { acc ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Hesap adı") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = { onRename(acc, renameText.trim()); renaming = null }
                ) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
fun AccountDetailScreen(
    type: AccountType,
    group: String,
    accountId: Long,
    accounts: List<Account>,
    txns: List<Txn>,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onAddTxn: () -> Unit,
    onDelete: (Txn) -> Unit
) {
    val acc = accounts.firstOrNull { it.id == accountId }
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val periodic = type == AccountType.INCOME || type == AccountType.EXPENSE
    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    val list = txns.filter { t ->
        (t.fromId == accountId || t.toId == accountId) && (!periodic || t.date in start..end)
    }
    val v = bal[accountId] ?: 0L

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("${acc?.name ?: "Hesap"} (${money(v)})", onBack, onHome)
            Breadcrumb("${type.label} > $group")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PeriodPill(month, onMonth)
                WhiteBox {
                    Text(
                        "Bakiye: ${money(v)}",
                        Modifier.fillMaxWidth().padding(14.dp),
                        color = if (v < 0) NegativeRed else GreenText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Button(
                    onClick = onAddTxn,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = typeColor(type))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Yeni işlem gir")
                }
                TxnTable("İşlemler", list, byId, setOf(accountId)) { onDelete(it) }
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
    onMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var pie by remember { mutableStateOf(startPie) }
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val slices = remember(accounts, bal, type, group) {
        val raw = if (group == null) {
            groupTotals(accounts, type, bal)
        } else {
            accounts.filter { it.type == type && it.groupName == group }.map { it.name to (bal[it.id] ?: 0L) }
        }
        raw.map { it.first to abs(it.second) }.filter { it.second > 0L }
    }
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("${group ?: type.label} grafiği", onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PeriodPill(month, onMonth)
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = pie, onClick = { pie = true }, label = { Text("Pasta") })
                    FilterChip(selected = !pie, onClick = { pie = false }, label = { Text("Çubuk") })
                }
                if (slices.isEmpty()) {
                    WhiteBox { Text("Bu dönemde gösterilecek veri yok.", Modifier.padding(16.dp)) }
                } else {
                    WhiteBox {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (pie) PieChart(slices) else BarChart(slices)
                        }
                    }
                    WhiteBox {
                        slices.forEachIndexed { i, sl ->
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(14.dp).background(PALETTE[i % PALETTE.size]))
                                Spacer(Modifier.width(8.dp))
                                Text(sl.first, Modifier.weight(1f))
                                Text(money(sl.second))
                            }
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

fun typeOfMode(m: TxMode): AccountType = when (m) {
    TxMode.INCOME_IN -> AccountType.INCOME
    TxMode.EXPENSE_CASH, TxMode.EXPENSE_DEBT -> AccountType.EXPENSE
    TxMode.TRANSFER -> AccountType.ASSET
    TxMode.BORROW, TxMode.REPAY -> AccountType.LIABILITY
}

/** Bir işlem tipi: hangi çift taraflı kayıt kalıbı ve hangi kategorilerden başlayacağı. */
data class Preset(val title: String, val mode: TxMode, val fromGroup: String?, val toGroup: String?)

const val G_CASH = "Nakit"
const val G_BANK = "Banka hesapları"
const val G_CARD = "Kredi kartları"
const val G_LOAN = "Krediler"
const val G_PRIV = "Özel borçlar"
const val G_BILLS = "Faturalar"
const val G_OTHER_EXP = "Diğer giderler"

val PRESETS = listOf(
    Preset("Kart harcaması", TxMode.EXPENSE_DEBT, G_CARD, null),
    Preset("Nakit harcama", TxMode.EXPENSE_CASH, G_CASH, null),
    Preset("Hesaptan harcama", TxMode.EXPENSE_CASH, G_BANK, null),
    Preset("Para çekme", TxMode.TRANSFER, G_BANK, G_CASH),
    Preset("Para transferi", TxMode.TRANSFER, G_BANK, G_BANK),
    Preset("Kart ödemesi", TxMode.REPAY, G_BANK, G_CARD),
    Preset("Kart kullandırma", TxMode.BORROW, G_CARD, G_BANK),
    Preset("Kredi ödemesi", TxMode.REPAY, G_BANK, G_LOAN),
    Preset("Gelir girme", TxMode.INCOME_IN, null, G_BANK),
    Preset("Fatura ödeme", TxMode.EXPENSE_CASH, G_BANK, G_BILLS)
)

fun optionsFor(type: AccountType, group: String?): List<Preset> = when (type) {
    AccountType.EXPENSE -> listOf(
        Preset("Nakit harcama", TxMode.EXPENSE_CASH, G_CASH, group),
        Preset("Kart harcaması", TxMode.EXPENSE_DEBT, G_CARD, group),
        Preset("Hesaptan harcama", TxMode.EXPENSE_CASH, G_BANK, group),
        Preset("Borçlanarak harcama (Veresiye)", TxMode.EXPENSE_DEBT, G_PRIV, group)
    )
    AccountType.LIABILITY -> when (group) {
        G_CARD -> listOf(
            Preset("Kart harcaması", TxMode.EXPENSE_DEBT, G_CARD, null),
            Preset("Kart ödemesi", TxMode.REPAY, G_BANK, G_CARD),
            Preset("Nakit avans çekme", TxMode.BORROW, G_CARD, G_CASH),
            Preset("Bankacılık giderleri girme", TxMode.EXPENSE_DEBT, G_CARD, G_OTHER_EXP)
        )
        G_LOAN -> listOf(
            Preset("Kredi çekme", TxMode.BORROW, G_LOAN, G_BANK),
            Preset("Kredi ödemesi", TxMode.REPAY, G_BANK, G_LOAN)
        )
        null -> listOf(
            Preset("Kart harcaması", TxMode.EXPENSE_DEBT, G_CARD, null),
            Preset("Kart ödemesi", TxMode.REPAY, G_BANK, G_CARD),
            Preset("Nakit avans çekme", TxMode.BORROW, G_CARD, G_CASH),
            Preset("Borçlanma", TxMode.BORROW, G_PRIV, G_CASH),
            Preset("Borç ödeme", TxMode.REPAY, G_CASH, G_PRIV),
            Preset("Kredi çekme", TxMode.BORROW, G_LOAN, G_BANK),
            Preset("Kredi ödemesi", TxMode.REPAY, G_BANK, G_LOAN)
        )
        else -> listOf(
            Preset("Borçlanma", TxMode.BORROW, group, G_CASH),
            Preset("Borç ödeme", TxMode.REPAY, G_CASH, group)
        )
    }
    AccountType.ASSET -> listOf(
        Preset("Para transferi", TxMode.TRANSFER, group ?: G_BANK, G_BANK),
        Preset("Para çekme", TxMode.TRANSFER, G_BANK, G_CASH),
        Preset("Para yatırma", TxMode.TRANSFER, G_CASH, G_BANK)
    )
    AccountType.INCOME -> listOf(Preset("Gelir girme", TxMode.INCOME_IN, group, G_BANK))
}

enum class Plan(val label: String) {
    SINGLE("Tek seferlik"),
    INSTALLMENT("Taksit"),
    REPEAT("Tekrar")
}

enum class Per(val label: String) {
    WEEK("Haftalık"),
    MONTH("Aylık"),
    YEAR("Yıllık");

    fun shift(d: LocalDate, k: Long): LocalDate = when (this) {
        WEEK -> d.plusWeeks(k)
        MONTH -> d.plusMonths(k)
        YEAR -> d.plusYears(k)
    }
}

@Composable
fun SelectorRow(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEFEFEF))), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), color = GreenText, style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GreenText)
    }
}

@Composable
fun NewAccountButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .shadow(4.dp, shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF2E9E6A), Color(0xFF176B45))), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Yeni Hesap", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun AccountPickerPane(
    title: String,
    options: List<Account>,
    bal: Map<Long, Long>,
    onPick: (Account) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, onBack, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (options.isEmpty()) NewAccountButton(onNew)
                options.forEach { a ->
                    val v = bal[a.id] ?: 0L
                    WhiteBox {
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(a) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(a.groupName, fontSize = 10.sp, color = Color.Gray)
                            }
                            Text(money(v), color = if (v < 0) NegativeRed else Color.DarkGray, fontSize = 13.sp)
                        }
                    }
                }
                if (options.isNotEmpty()) NewAccountButton(onNew)
            }
        }
    }
}

@Composable
fun AddTxnScreen(
    type: AccountType,
    accounts: List<Account>,
    bal: Map<Long, Long>,
    fav: Favorite?,
    preset: Preset?,
    accountId: Long?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNewAccount: (String, String, AccountType, (Account) -> Unit) -> Unit,
    onSave: (List<Txn>, Favorite?) -> Unit
) {
    val favFrom = fav?.let { f -> accounts.firstOrNull { it.id == f.fromId } }
    val favTo = fav?.let { f -> accounts.firstOrNull { it.id == f.toId } }
    val acc = accountId?.let { id -> accounts.firstOrNull { it.id == id } }
    val mode = remember {
        preset?.mode
            ?: TxMode.values().firstOrNull { it.from == favFrom?.type && it.to == favTo?.type }
            ?: modesFor(type).first()
    }
    var amountText by remember {
        mutableStateOf(fav?.let { String.format(Locale.US, "%.2f", it.cents / 100.0) } ?: "")
    }
    var note by remember { mutableStateOf(fav?.note ?: "") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDate by remember { mutableStateOf(false) }
    var taksit by remember { mutableStateOf(false) }
    var tekrar by remember { mutableStateOf(false) }
    var per by remember { mutableStateOf(Per.MONTH) }
    var countText by remember { mutableStateOf("") }
    var saveFav by remember { mutableStateOf(false) }
    var favName by remember { mutableStateOf(preset?.title ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val allFrom = accounts.filter { it.type == mode.from }
    val allTo = accounts.filter { it.type == mode.to }
    val fromOpts = preset?.fromGroup?.let { g -> allFrom.filter { it.groupName == g }.ifEmpty { allFrom } } ?: allFrom
    val toOpts = preset?.toGroup?.let { g -> allTo.filter { it.groupName == g }.ifEmpty { allTo } } ?: allTo
    val accIsFrom: Boolean? = when {
        acc == null -> null
        preset?.fromGroup == acc.groupName && acc.type == mode.from -> true
        preset?.toGroup == acc.groupName && acc.type == mode.to -> false
        acc.type == mode.from -> true
        acc.type == mode.to -> false
        else -> null
    }
    var from by remember { mutableStateOf(favFrom ?: (if (accIsFrom == true) acc else null) ?: fromOpts.singleOrNull()) }
    var to by remember { mutableStateOf(favTo ?: (if (accIsFrom == false) acc else null) ?: toOpts.singleOrNull()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf<Int?>(null) }
    val plan = if (taksit) Plan.INSTALLMENT else if (tekrar) Plan.REPEAT else Plan.SINGLE

    BackHandler(enabled = picking != null || creating != null) {
        if (creating != null) creating = null else picking = null
    }

    fun sideType(side: Int): AccountType = if (side == 0) mode.from else mode.to
    fun sideGroup(side: Int): String? = if (side == 0) preset?.fromGroup else preset?.toGroup

    val creatingSide = creating
    val pickingSide = picking
    when {
        creatingSide != null -> {
            val t = sideType(creatingSide)
            AddAccountScreen(
                t, sideGroup(creatingSide) ?: "", accounts,
                onBack = { creating = null },
                onHome = onHome,
                onSave = { name, group ->
                    onNewAccount(name, group, t) { a ->
                        if (creatingSide == 0) from = a else to = a
                        creating = null
                        picking = null
                    }
                }
            )
        }
        pickingSide != null -> {
            val t = sideType(pickingSide)
            val g = sideGroup(pickingSide)
            val opts = accounts.filter { it.type == t }.sortedBy { if (it.groupName == g) 0 else 1 }
            AccountPickerPane(
                "${t.label} hesabı seçiniz:", opts, bal,
                onPick = { a ->
                    if (pickingSide == 0) from = a else to = a
                    picking = null
                },
                onNew = { creating = pickingSide },
                onBack = { picking = null },
                onHome = onHome
            )
        }
        else -> AppBackground {
            Column(Modifier.fillMaxSize()) {
                TopBar("${type.label} yönetimi", onBack, onHome)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactField(amountText, { amountText = it }, "0.00", KeyboardType.Decimal, TextAlign.End, "TL")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CalendarBadge(Modifier.size(44.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                            Modifier
                                .weight(1f)
                                .shadow(3.dp, RoundedCornerShape(12.dp))
                                .background(Color(0xFFE8E8E8), RoundedCornerShape(12.dp))
                                .clickable { showDate = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            color = Color.DarkGray,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    CompactField(note, { note = it }, "Açıklama giriniz")
                    Text("Kaynak hesabı seçiniz:", Modifier.padding(start = 4.dp))
                    SelectorRow(from?.name ?: "Hesap seçiniz") { picking = 0 }
                    Text("Hedef hesabı seçiniz:", Modifier.padding(start = 4.dp))
                    SelectorRow(to?.name ?: "Hesap seçiniz") { picking = 1 }
                    WhiteBox {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Taksit: ")
                                Switch(checked = taksit, onCheckedChange = { taksit = it; if (it) tekrar = false })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("İşlem Tekrarı: ")
                                Switch(checked = tekrar, onCheckedChange = { tekrar = it; if (it) taksit = false })
                            }
                        }
                        if (plan != Plan.SINGLE) {
                            Column(
                                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactField(
                                    countText, { countText = it },
                                    if (plan == Plan.INSTALLMENT) "Taksit sayısı" else "Tekrar sayısı",
                                    KeyboardType.Number, flat = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Per.values().forEach { p ->
                                        FilterChip(selected = p == per, onClick = { per = p }, label = { Text(p.label) })
                                    }
                                }
                            }
                        }
                    }
                    WhiteBox {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = saveFav, onCheckedChange = { saveFav = it })
                            Text("Sık kullanılanlara ekle", color = GreenText, style = MaterialTheme.typography.titleMedium)
                        }
                        if (saveFav) {
                            Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                CompactField(favName, { favName = it }, "Kısayol adı", flat = true)
                            }
                        }
                    }
                    error?.let { Text(it, color = NegativeRed) }
                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E8A5E)),
                        onClick = {
                            val cents = amountText.trim().replace(',', '.').toBigDecimalOrNull()
                                ?.multiply(BigDecimal(100))?.toLong() ?: 0L
                            val f = from
                            val t = to
                            val n = if (plan == Plan.SINGLE) 1 else (countText.toIntOrNull() ?: 0)
                            when {
                                cents <= 0L -> error = "Geçerli bir tutar girin."
                                f == null -> error = "Kaynak hesabı seçin."
                                t == null -> error = "Hedef hesabı seçin."
                                f.id == t.id -> error = "Kaynak ve hedef farklı olmalı."
                                plan != Plan.SINGLE && (n < 2 || n > 120) -> error = "Sayı 2 ile 120 arasında olmalı."
                                saveFav && favName.isBlank() -> error = "Kısayol adı girin."
                                else -> {
                                    val list = (0 until n).map { k ->
                                        val c = if (plan == Plan.INSTALLMENT) {
                                            cents / n + (if (k == 0) cents % n else 0L)
                                        } else cents
                                        val label = if (plan == Plan.SINGLE) note.trim()
                                        else listOf(note.trim(), "(${k + 1}/$n)").filter { it.isNotEmpty() }.joinToString(" ")
                                        Txn(
                                            cents = c,
                                            date = per.shift(date, k.toLong()).toEpochDay(),
                                            note = label,
                                            fromId = f.id,
                                            toId = t.id
                                        )
                                    }
                                    val favorite = if (saveFav) {
                                        Favorite(name = favName.trim(), cents = cents, note = note.trim(), fromId = f.id, toId = t.id)
                                    } else null
                                    onSave(list, favorite)
                                }
                            }
                        }
                    ) { Text("Kaydet", style = MaterialTheme.typography.titleMedium) }
                }
            }
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
fun AddAccountScreen(
    type: AccountType,
    initialGroup: String,
    accounts: List<Account>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSave: (name: String, group: String) -> Unit
) {
    val groups = remember(accounts, type) { accounts.filter { it.type == type }.map { it.groupName }.distinct() }
    var name by remember { mutableStateOf("") }
    var chosen by remember {
        mutableStateOf(if (initialGroup.isNotEmpty()) initialGroup else (groups.firstOrNull() ?: "Genel"))
    }
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Yeni hesap", onBack, onHome)
            if (initialGroup.isNotEmpty()) Breadcrumb("${type.label} > $initialGroup")
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompactField(name, { name = it; error = null }, "Hesap adı", flat = true)
                        if (initialGroup.isEmpty()) {
                            Text("${type.label} kategorisi seçiniz:")
                            Box {
                                SelectorRow(chosen) { open = true }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    groups.forEach { g ->
                                        DropdownMenuItem(text = { Text(g) }, onClick = { chosen = g; open = false })
                                    }
                                }
                            }
                        }
                        error?.let { Text(it, color = NegativeRed) }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = typeColor(type)),
                            onClick = {
                                val n = name.trim()
                                when {
                                    n.isEmpty() -> error = "Hesap adı girin."
                                    accounts.any {
                                        it.type == type && it.groupName == chosen && it.name.equals(n, ignoreCase = true)
                                    } -> error = "Bu kategoride aynı adlı hesap var."
                                    else -> onSave(n, chosen)
                                }
                            }
                        ) { Text("Kaydet") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(tint, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
fun DailyCard(accounts: List<Account>, txns: List<Txn>) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val dayTxns = remember(txns, day) { txns.filter { it.date == day.toEpochDay() } }
    val income = dayTxns.filter { byId[it.fromId]?.type == AccountType.INCOME }.sumOf { it.cents }
    val expense = dayTxns.filter { byId[it.toId]?.type == AccountType.EXPENSE }.sumOf { it.cents }
    val net = income - expense
    val title = if (day == LocalDate.now()) "Bugün" else day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", TR))
    val green = Color(0xFF1E8A5E)
    val red = Color(0xFFB3261E)

    WhiteBox {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFEFEFEF)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                Modifier.clickable { day = day.minusDays(1) }.padding(horizontal = 18.dp, vertical = 6.dp),
                fontSize = 24.sp,
                color = GreenText
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Günlük işlemler", fontWeight = FontWeight.Bold, color = GreenText, fontSize = 14.sp)
                Text(title, fontSize = 11.sp, color = Color.Gray)
            }
            Text(
                "›",
                Modifier.clickable { day = day.plusDays(1) }.padding(horizontal = 18.dp, vertical = 6.dp),
                fontSize = 24.sp,
                color = GreenText
            )
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Gelir", money(income), green, Color(0xFFE6F4EC), Modifier.weight(1f))
            StatBox("Gider", money(expense), red, Color(0xFFF9E6E4), Modifier.weight(1f))
            StatBox(
                "Net",
                (if (net > 0) "+" else "") + money(net),
                if (net >= 0) green else red,
                Color(0xFFEFEFEF),
                Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFFF2F2F2)).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text("İşlem", Modifier.weight(1f), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text("Tutar", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        if (dayTxns.isEmpty()) {
            Text("Bu günde işlem yok.", Modifier.padding(14.dp), color = Color.Gray, fontSize = 12.sp)
        }
        dayTxns.forEachIndexed { i, t ->
            val from = byId[t.fromId]
            val to = byId[t.toId]
            val sign = if (from?.type == AccountType.INCOME) 1 else if (to?.type == AccountType.EXPENSE) -1 else 0
            val head = when (sign) {
                1 -> from?.name ?: "Gelir"
                -1 -> to?.name ?: "Gider"
                else -> "${from?.name ?: "?"} → ${to?.name ?: "?"}"
            }
            val sub = when (sign) {
                1 -> "→ ${to?.name ?: "?"}"
                -1 -> "← ${from?.name ?: "?"}"
                else -> "Hesaplar arası"
            } + if (t.note.isNotBlank()) " · ${t.note}" else ""
            TxnLine(head, sub, t.cents, sign, i % 2 == 1, null)
            HorizontalDivider(color = Color(0xFFE3E3E3))
        }
        Spacer(Modifier.height(4.dp))
    }
}
