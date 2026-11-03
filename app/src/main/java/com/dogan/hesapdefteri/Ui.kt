@file:OptIn(ExperimentalMaterial3Api::class)

package com.dogan.hesapdefteri

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
    data class AddAccount(val type: AccountType, val group: String, val parentId: Long?, val back: Screen) : Screen
    data class EditTxn(val txnId: Long, val back: Screen) : Screen
    data object Favorites : Screen
    data object Update : Screen
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
        if (periodic) {
            if (date < start || date > end) return
        } else if (date > end) {
            return
        }
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

fun subtreeIds(accounts: List<Account>, rootId: Long): Set<Long> {
    val out = HashSet<Long>()
    val stack = ArrayList<Long>()
    stack.add(rootId)
    while (stack.isNotEmpty()) {
        val id = stack.removeAt(stack.size - 1)
        if (out.add(id)) accounts.filter { it.parentId == id }.forEach { stack.add(it.id) }
    }
    return out
}

fun rollupBalances(accounts: List<Account>, bal: Map<Long, Long>): Map<Long, Long> {
    val kids = accounts.groupBy { it.parentId }
    val res = HashMap<Long, Long>()
    fun total(id: Long): Long {
        val known = res[id]
        if (known != null) return known
        val t = (bal[id] ?: 0L) + (kids[id]?.sumOf { total(it.id) } ?: 0L)
        res[id] = t
        return t
    }
    accounts.forEach { total(it.id) }
    return res
}

fun flatTree(list: List<Account>): List<Pair<Account, Int>> {
    val kids = list.groupBy { it.parentId }
    val out = ArrayList<Pair<Account, Int>>()
    fun walk(parent: Long?, depth: Int) {
        kids[parent]?.forEach {
            out.add(it to depth)
            walk(it.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

/** Kategori › üst kalemler (hesabın kendi adı hariç). */
fun trail(a: Account, byId: Map<Long, Account>): String {
    val parts = ArrayList<String>()
    var cur: Account? = a.parentId?.let { byId[it] }
    var guard = 0
    while (cur != null && guard < 20) {
        parts.add(0, cur.name)
        cur = cur.parentId?.let { byId[it] }
        guard++
    }
    parts.add(0, a.groupName)
    return parts.joinToString(" › ")
}

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
    var pendingUpdate by remember { mutableStateOf<Updater.Release?>(null) }

    LaunchedEffect(Unit) {
        try {
            val r = Updater.latest()
            if (r != null && r.code > Updater.currentCode(ctx)) pendingUpdate = r
        } catch (e: Exception) {
            // ağ yoksa sessizce geç
        }
    }

    var stoppedAt by remember { mutableStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = SystemClock.elapsedRealtime() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stoppedAt != 0L && PinStore.isSet(ctx) && SystemClock.elapsedRealtime() - stoppedAt > 30_000L) {
            unlocked = false
        }
    }

    fun goBack() {
        screen = when (val s = screen) {
            Screen.Home -> Screen.Home
            is Screen.Category -> Screen.Home
            is Screen.Group -> Screen.Category(s.type)
            is Screen.AccountDetail -> {
                val pid = accounts.firstOrNull { it.id == s.accountId }?.parentId
                if (pid != null) Screen.AccountDetail(s.type, s.group, pid) else Screen.Group(s.type, s.group)
            }
            is Screen.Chart -> if (s.group != null) Screen.Group(s.type, s.group) else Screen.Category(s.type)
            is Screen.TxTypes -> s.back
            is Screen.AddTxn -> s.back
            is Screen.AddAccount -> s.back
            is Screen.EditTxn -> s.back
            Screen.Favorites, Screen.Update, Screen.Settings, Screen.PinSetup, is Screen.Info -> Screen.Home
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
        "Güncellemeyi kontrol et" to { screen = Screen.Update },
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
            menu = menu,
            update = pendingUpdate,
            onOpenUpdate = { screen = Screen.Update },
            onEditTxn = { t -> screen = Screen.EditTxn(t.id, Screen.Home) }
        )
        is Screen.Category -> CategoryScreen(
            s.type, accounts, txns, month,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onAddTxn = { openNewTx(s.type, null, s) },
            onOpenGroup = { screen = Screen.Group(s.type, it) },
            onChart = { screen = Screen.Chart(s.type, null, it) },
            onAddAccount = { screen = Screen.AddAccount(s.type, "", null, s) }
        )
        is Screen.Group -> GroupScreen(
            s.type, s.group, accounts, txns, month,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onAddTxn = { openNewTx(s.type, s.group, s) },
            onAddAccount = { parent -> screen = Screen.AddAccount(s.type, s.group, parent?.id, s) },
            onChart = { screen = Screen.Chart(s.type, s.group, it) },
            onOpenAccount = { a -> screen = Screen.AccountDetail(s.type, s.group, a.id) },
            onRename = { a, n -> vm.updateAccount(a.copy(name = n)) },
            onDeleteAccount = { vm.deleteAccount(it) },
            onEdit = { t -> screen = Screen.EditTxn(t.id, s) },
            onDelete = { vm.deleteTxn(it) }
        )
        is Screen.AccountDetail -> AccountDetailScreen(
            s.type, s.group, s.accountId, accounts, txns, month,
            onMonth = { month = it },
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onAddTxn = { openNewTx(s.type, s.group, s, s.accountId) },
            onOpenAccount = { a -> screen = Screen.AccountDetail(s.type, s.group, a.id) },
            onAddSub = { screen = Screen.AddAccount(s.type, s.group, s.accountId, s) },
            onUpdateAccount = { vm.updateAccount(it) },
            onEdit = { t -> screen = Screen.EditTxn(t.id, s) },
            onDelete = { vm.deleteTxn(it) }
        )
        is Screen.Chart -> ChartScreen(
            s.type, s.group, accounts, txns, month, s.pie,
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
                s.type, accounts, bal, s.fav, s.preset, s.accountId, null,
                onBack = { goBack() },
                onHome = { screen = Screen.Home },
                onNewAccount = { name, group, type, parentId, limit, cutoff, done ->
                    vm.addAccount(name, group, type, parentId, limit, cutoff, done)
                },
                onSave = { list, fav ->
                    vm.addTxns(list, fav)
                    screen = (s.back as? Screen.TxTypes)?.back ?: s.back
                }
            )
        }
        is Screen.AddAccount -> AddAccountScreen(
            s.type, s.group, s.parentId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts,
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onSave = { name, group, limit, cutoff ->
                vm.addAccount(name, group, s.type, s.parentId, limit, cutoff)
                goBack()
            }
        )
        is Screen.EditTxn -> {
            val t = txns.firstOrNull { it.id == s.txnId }
            if (t == null) {
                LaunchedEffect(s) { screen = s.back }
            } else {
                val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
                val fT = accounts.firstOrNull { it.id == t.fromId }?.type
                val tT = accounts.firstOrNull { it.id == t.toId }?.type
                val m = TxMode.values().firstOrNull { it.from == fT && it.to == tT } ?: TxMode.TRANSFER
                AddTxnScreen(
                    typeOfMode(m), accounts, bal, null, null, null, t,
                    onBack = { goBack() },
                    onHome = { screen = Screen.Home },
                    onNewAccount = { name, group, type, parentId, limit, cutoff, done ->
                        vm.addAccount(name, group, type, parentId, limit, cutoff, done)
                    },
                    onSave = { _, _ -> },
                    onUpdate = { u ->
                        vm.updateTxn(u)
                        screen = s.back
                    }
                )
            }
        }
        Screen.Favorites -> FavoritesScreen(
            favorites,
            onPreset = { p -> screen = Screen.AddTxn(typeOfMode(p.mode), null, p, null, Screen.Favorites) },
            onUseFav = { f -> screen = Screen.AddTxn(favType(accounts, f), f, null, null, Screen.Favorites) },
            onDeleteFav = { vm.deleteFavorite(it) },
            onBack = { goBack() },
            onHome = { screen = Screen.Home }
        )
        Screen.Update -> UpdateScreen(
            pendingUpdate,
            onBack = { goBack() },
            onHome = { screen = Screen.Home }
        )
        Screen.Settings -> SettingsScreen(
            onBack = { goBack() },
            onHome = { screen = Screen.Home },
            onReset = { vm.resetAll() },
            onExport = { vm.exportJson() },
            onImport = { text, done -> vm.importJson(text, done) }
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
    menu: List<Pair<String, () -> Unit>>,
    update: Updater.Release?,
    onOpenUpdate: () -> Unit,
    onEditTxn: (Txn) -> Unit
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
                    if (update != null) {
                        WhiteBox {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onOpenUpdate).padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Yeni sürüm var (build ${update.code}). Güncellemek için dokun.", Modifier.weight(1f), color = GreenText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("›", fontSize = 22.sp, color = GreenText)
                            }
                        }
                    }
                    PeriodPill(month, onMonth)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TypeCard(AccountType.ASSET, total(AccountType.ASSET), { onOpen(AccountType.ASSET) }, { onAdd(AccountType.ASSET) }, Modifier.weight(1f))
                        TypeCard(AccountType.LIABILITY, total(AccountType.LIABILITY), { onOpen(AccountType.LIABILITY) }, { onAdd(AccountType.LIABILITY) }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TypeCard(AccountType.INCOME, total(AccountType.INCOME), { onOpen(AccountType.INCOME) }, { onAdd(AccountType.INCOME) }, Modifier.weight(1f))
                        TypeCard(AccountType.EXPENSE, total(AccountType.EXPENSE), { onOpen(AccountType.EXPENSE) }, { onAdd(AccountType.EXPENSE) }, Modifier.weight(1f))
                    }
                    StatusCard(accounts, txns)
                    FavoritesEntry(onOpenFavs)
                    DailyCard(accounts, txns, onEditTxn)
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
fun TxnLine(
    title: String,
    subtitle: String,
    cents: Long,
    sign: Int,
    zebra: Boolean,
    onDelete: (() -> Unit)?,
    onClick: (() -> Unit)? = null
) {
    val color = if (sign > 0) Color(0xFF1E8A5E) else if (sign < 0) Color(0xFFB3261E) else Color(0xFF555555)
    val prefix = if (sign > 0) "+" else if (sign < 0) "−" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (zebra) Color(0xFFF6F6F6) else Color.Transparent)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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
    onEdit: ((Txn) -> Unit)? = null,
    onDelete: ((Txn) -> Unit)?
) {
    var pendingDelete by remember { mutableStateOf<Txn?>(null) }
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
                if (onDelete != null) ({ pendingDelete = t }) else null,
                if (onEdit != null) ({ onEdit(t) }) else null
            )
            HorizontalDivider(color = Color(0xFFE3E3E3))
        }
    }
    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("İşlem silinsin mi?") },
            text = {
                Text(
                    "${byId[t.fromId]?.name ?: "?"} → ${byId[t.toId]?.name ?: "?"}  ${money(t.cents)}\n${dayText(t.date)}"
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(t); pendingDelete = null }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") } }
        )
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
    onAddAccount: (Account?) -> Unit,
    onChart: (Boolean) -> Unit,
    onOpenAccount: (Account) -> Unit,
    onRename: (Account, String) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onEdit: (Txn) -> Unit,
    onDelete: (Txn) -> Unit
) {
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val roll = remember(accounts, bal) { rollupBalances(accounts, bal) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val mine = accounts.filter { it.type == type && it.groupName == group }
    val tree = remember(mine) { flatTree(mine) }
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
                        "Düzenleme açık: bir kalemin adına dokunarak adını değiştir, çöp kutusuyla sil.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                message?.let { Text(it, color = NegativeRed) }
                WhiteBox {
                    SectionHeader("Hesaplar ve alt kalemler")
                    tree.forEach { (a, depth) ->
                        val v = roll[a.id] ?: 0L
                        val rowCard = if (a.type == AccountType.LIABILITY && a.groupName == G_CARD && a.limitCents != null) {
                            cardInfo(a, accounts, txns)
                        } else null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (editMode) { renaming = a; renameText = a.name } else onOpenAccount(a) }
                                .padding(start = (14 + depth * 18).dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(
                                    a.name,
                                    color = GreenText,
                                    fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Medium
                                )
                                val rem = rowCard?.remaining
                                if (rem != null) {
                                    Text(
                                        "Kalan limit: " + money(rem),
                                        fontSize = 10.sp,
                                        color = if (rem < 0) NegativeRed else Color.Gray
                                    )
                                }
                            }
                            Text(money(v), color = if (v < 0) NegativeRed else Color.DarkGray)
                            if (editMode) {
                                IconButton(onClick = {
                                    val hasKids = mine.any { it.parentId == a.id }
                                    if (hasKids) {
                                        message = "“${a.name}” altında kalemler var, önce onları sil."
                                    } else if (txns.any { it.fromId == a.id || it.toId == a.id }) {
                                        message = "“${a.name}” kaleminde işlem var, silinemez."
                                    } else {
                                        message = null
                                        onDeleteAccount(a)
                                    }
                                }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(onClick = { onAddAccount(a) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Alt kalem ekle", tint = GreenText, modifier = Modifier.size(20.dp))
                                }
                                Text("›", color = Color.Gray, fontSize = 20.sp)
                            }
                        }
                        HorizontalDivider()
                    }
                    Box(Modifier.padding(10.dp)) {
                        AddPanel("Yeni hesap ekle", typeColor(type)) { onAddAccount(null) }
                    }
                }
                TxnTable("İşlemler", list, byId, ids, onEdit = { onEdit(it) }) { onDelete(it) }
            }
        }
    }

    renaming?.let { acc ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Kalem adı") },
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
fun InfoRow(label: String, value: String, color: Color = Color.DarkGray, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun CardSummary(info: CardInfo, onEdit: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val green = Color(0xFF1E8A5E)
    WhiteBox {
        SectionHeader("Kart özeti")
        Spacer(Modifier.height(4.dp))
        val limit = info.limit
        val remaining = info.remaining
        if (limit != null && remaining != null) {
            InfoRow("Kart limiti", money(limit))
            InfoRow("Kalan limit", money(remaining), if (remaining < 0) NegativeRed else green, bold = true)
        } else {
            Text("Kart limiti girilmemiş.", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, color = Color.Gray)
        }
        InfoRow("Güncel toplam borç", money(info.debtToday), NegativeRed, bold = true)
        val last = info.lastCutoff
        val next = info.nextCutoff
        if (last != null && next != null) {
            HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = Color(0xFFDDDDDD))
            InfoRow("Ekstre borcu (kesim ${last.format(fmt)})", money(info.statementDebt), Color(0xFF3F6E80), bold = true)
            InfoRow("Dönem içi harcama (ekstreye dahil değil)", money(info.periodSpend))
            InfoRow("Sonraki hesap kesim tarihi", next.format(fmt))
        } else {
            Text("Hesap kesim günü girilmemiş.", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, color = Color.Gray)
        }
        if (info.futureCharges > 0) {
            InfoRow("Gelecek taksitler", money(info.futureCharges))
        }
        Box(Modifier.padding(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Limit ve hesap kesim gününü düzenle") }
        }
    }
}

@Composable
fun CardSettingsDialog(acc: Account, onDismiss: () -> Unit, onSave: (Account) -> Unit) {
    var limitText by remember {
        mutableStateOf(acc.limitCents?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: "")
    }
    var dayText by remember { mutableStateOf(acc.cutoffDay?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kart ayarları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactField(limitText, { limitText = it }, "Kart limiti (TL)", KeyboardType.Decimal, flat = true)
                CompactField(dayText, { dayText = it }, "Hesap kesim günü (1-31)", KeyboardType.Number, flat = true)
                error?.let { Text(it, color = NegativeRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limit = if (limitText.isBlank()) null else parseCents(limitText)
                val day = if (dayText.isBlank()) null else dayText.trim().toIntOrNull()
                when {
                    limitText.isNotBlank() && (limit == null || limit < 0) -> error = "Geçerli bir limit girin."
                    dayText.isNotBlank() && (day == null || day < 1 || day > 31) -> error = "Gün 1 ile 31 arasında olmalı."
                    else -> onSave(acc.copy(limitCents = limit, cutoffDay = day))
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
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
    onOpenAccount: (Account) -> Unit,
    onAddSub: () -> Unit,
    onUpdateAccount: (Account) -> Unit,
    onEdit: (Txn) -> Unit,
    onDelete: (Txn) -> Unit
) {
    val acc = accounts.firstOrNull { it.id == accountId }
    val bal = remember(accounts, txns, month) { balances(accounts, txns, month) }
    val roll = remember(accounts, bal) { rollupBalances(accounts, bal) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val ids = remember(accounts, accountId) { subtreeIds(accounts, accountId) }
    val kids = accounts.filter { it.parentId == accountId }
    val periodic = type == AccountType.INCOME || type == AccountType.EXPENSE
    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    val list = txns.filter { t ->
        (t.fromId in ids || t.toId in ids) && (!periodic || t.date in start..end)
    }
    val v = roll[accountId] ?: 0L
    val crumb = if (acc != null) "${type.label} › ${trail(acc, byId)}" else "${type.label} › $group"
    val isCard = acc != null && acc.type == AccountType.LIABILITY && acc.groupName == G_CARD && acc.parentId == null
    val info = remember(accounts, txns, acc) { if (isCard && acc != null) cardInfo(acc, accounts, txns) else null }
    var cardDialog by remember { mutableStateOf(false) }
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("${acc?.name ?: "Hesap"} (${money(v)})", onBack, onHome)
            Breadcrumb(crumb)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isCard) PeriodPill(month, onMonth)
                if (info != null) {
                    CardSummary(info) { cardDialog = true }
                } else {
                    WhiteBox {
                        Text(
                            "Bakiye: ${money(v)}",
                            Modifier.fillMaxWidth().padding(14.dp),
                            color = if (v < 0) NegativeRed else GreenText,
                            style = MaterialTheme.typography.titleMedium
                        )
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
                WhiteBox {
                    SectionHeader("Alt kalemler")
                    if (kids.isEmpty()) {
                        Text("Henüz alt kalem yok.", Modifier.padding(14.dp), color = Color.Gray, fontSize = 12.sp)
                    }
                    kids.forEach { k ->
                        val kv = roll[k.id] ?: 0L
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenAccount(k) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(k.name, Modifier.weight(1f), color = GreenText, fontWeight = FontWeight.Bold)
                            Text(money(kv), color = if (kv < 0) NegativeRed else Color.DarkGray)
                            Text("›", Modifier.padding(start = 8.dp), color = Color.Gray, fontSize = 20.sp)
                        }
                        HorizontalDivider()
                    }
                    Box(Modifier.padding(10.dp)) {
                        AddPanel("Alt kalem ekle", typeColor(type), onAddSub)
                    }
                }
                val lastC = info?.lastCutoff
                val prevC = info?.prevCutoff
                if (info != null && lastC != null && prevC != null) {
                    val l = lastC.toEpochDay()
                    val p = prevC.toEpochDay()
                    TxnTable("Güncel dönem (ekstreye dahil değil)", list.filter { it.date > l }, byId, ids, onEdit = { onEdit(it) }) { onDelete(it) }
                    TxnTable("Son ekstre · kesim ${lastC.format(dateFmt)}", list.filter { it.date > p && it.date <= l }, byId, ids, onEdit = { onEdit(it) }) { onDelete(it) }
                    val older = list.filter { it.date <= p }
                    if (older.isNotEmpty()) TxnTable("Önceki hareketler", older, byId, ids, onEdit = { onEdit(it) }) { onDelete(it) }
                } else {
                    TxnTable("İşlemler", list, byId, ids, onEdit = { onEdit(it) }) { onDelete(it) }
                }
            }
        }
    }

    if (cardDialog && acc != null) {
        CardSettingsDialog(acc, onDismiss = { cardDialog = false }) {
            onUpdateAccount(it)
            cardDialog = false
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
const val G_BILLS = "Fatura Giderleri"
const val G_OTHER_EXP = "Diğer"

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
fun NewAccountButton(label: String = "Yeni Hesap", onClick: () -> Unit) {
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
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

sealed interface PickNode {
    data class G(val name: String) : PickNode
    data class A(val id: Long) : PickNode
}

@Composable
fun PickRow(title: String, amount: Long, hasChildren: Boolean, onAdd: (() -> Unit)?, onClick: () -> Unit) {
    WhiteBox {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f).padding(vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(money(amount), color = if (amount < 0) NegativeRed else Color.DarkGray, fontSize = 13.sp)
            if (onAdd != null) {
                IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Alt kalem ekle", tint = GreenText, modifier = Modifier.size(20.dp))
                }
            }
            if (hasChildren) Text("›", Modifier.padding(end = 8.dp), color = Color.Gray, fontSize = 20.sp)
        }
    }
}

@Composable
fun HierarchyPickerPane(
    title: String,
    type: AccountType,
    accounts: List<Account>,
    bal: Map<Long, Long>,
    presetGroup: String?,
    onPick: (Account) -> Unit,
    onNew: (String, Account?) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var path by remember { mutableStateOf<List<PickNode>>(emptyList()) }
    val roll = remember(accounts, bal) { rollupBalances(accounts, bal) }
    val ofType = accounts.filter { it.type == type }
    BackHandler(enabled = path.isNotEmpty()) { path = path.dropLast(1) }
    val node = path.lastOrNull()
    val heading = when (node) {
        null -> title
        is PickNode.G -> node.name
        is PickNode.A -> accounts.firstOrNull { it.id == node.id }?.name ?: title
    }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(heading, { if (path.isEmpty()) onBack() else path = path.dropLast(1) }, onHome)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (node) {
                    null -> {
                        val groups = ofType.map { it.groupName }.distinct().sortedBy { if (it == presetGroup) 0 else 1 }
                        if (groups.isEmpty()) NewAccountButton { onNew("", null) }
                        groups.forEach { g ->
                            val sum = ofType.filter { it.groupName == g }.sumOf { bal[it.id] ?: 0L }
                            PickRow(g, sum, true, null) { path = listOf(PickNode.G(g)) }
                        }
                    }
                    is PickNode.G -> {
                        val list = ofType.filter { it.groupName == node.name && it.parentId == null }
                        if (list.isEmpty()) NewAccountButton { onNew(node.name, null) }
                        list.forEach { a ->
                            val hasKids = ofType.any { it.parentId == a.id }
                            PickRow(a.name, roll[a.id] ?: 0L, hasKids, { onNew(a.groupName, a) }) {
                                if (hasKids) path = path + PickNode.A(a.id) else onPick(a)
                            }
                        }
                        if (list.isNotEmpty()) NewAccountButton { onNew(node.name, null) }
                    }
                    is PickNode.A -> {
                        val parent = accounts.firstOrNull { it.id == node.id }
                        if (parent != null) {
                            WhiteBox {
                                Row(
                                    Modifier.fillMaxWidth().clickable { onPick(parent) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Bu kalemi seç: ${parent.name}", Modifier.weight(1f), color = GreenText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("✓", color = GreenText, fontSize = 18.sp)
                                }
                            }
                            ofType.filter { it.parentId == parent.id }.forEach { k ->
                                val hasKids = ofType.any { it.parentId == k.id }
                                PickRow(k.name, roll[k.id] ?: 0L, hasKids, { onNew(k.groupName, k) }) {
                                    if (hasKids) path = path + PickNode.A(k.id) else onPick(k)
                                }
                            }
                            NewAccountButton("Alt Kalem Ekle") { onNew(parent.groupName, parent) }
                        }
                    }
                }
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
    editTxn: Txn?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNewAccount: (String, String, AccountType, Long?, Long?, Int?, (Account) -> Unit) -> Unit,
    onSave: (List<Txn>, Favorite?) -> Unit,
    onUpdate: (Txn) -> Unit = {}
) {
    val favFrom = fav?.let { f -> accounts.firstOrNull { it.id == f.fromId } }
    val favTo = fav?.let { f -> accounts.firstOrNull { it.id == f.toId } }
    val acc = accountId?.let { id -> accounts.firstOrNull { it.id == id } }
    val eFrom = editTxn?.let { e -> accounts.firstOrNull { it.id == e.fromId } }
    val eTo = editTxn?.let { e -> accounts.firstOrNull { it.id == e.toId } }
    val editing = editTxn != null
    val mode = remember {
        preset?.mode
            ?: (if (editTxn != null) TxMode.values().firstOrNull { it.from == eFrom?.type && it.to == eTo?.type } else null)
            ?: TxMode.values().firstOrNull { it.from == favFrom?.type && it.to == favTo?.type }
            ?: modesFor(type).first()
    }
    var amountText by remember {
        mutableStateOf((editTxn?.cents ?: fav?.cents)?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: "")
    }
    var note by remember { mutableStateOf(editTxn?.note ?: fav?.note ?: "") }
    var date by remember { mutableStateOf(editTxn?.let { LocalDate.ofEpochDay(it.date) } ?: LocalDate.now()) }
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
    var from by remember { mutableStateOf(eFrom ?: favFrom ?: (if (accIsFrom == true) acc else null) ?: fromOpts.singleOrNull()) }
    var to by remember { mutableStateOf(eTo ?: favTo ?: (if (accIsFrom == false) acc else null) ?: toOpts.singleOrNull()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf<Int?>(null) }
    var creatingGroup by remember { mutableStateOf("") }
    var creatingParent by remember { mutableStateOf<Account?>(null) }
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
                t, creatingGroup, creatingParent, accounts,
                onBack = { creating = null },
                onHome = onHome,
                onSave = { name, group, limit, cutoff ->
                    onNewAccount(name, group, t, creatingParent?.id, limit, cutoff) { a ->
                        if (creatingSide == 0) from = a else to = a
                        creating = null
                        picking = null
                    }
                }
            )
        }
        pickingSide != null -> {
            val t = sideType(pickingSide)
            HierarchyPickerPane(
                "${t.label} hesabı seçiniz:", t, accounts, bal, sideGroup(pickingSide),
                onPick = { a ->
                    if (pickingSide == 0) from = a else to = a
                    picking = null
                },
                onNew = { group, parent ->
                    creatingGroup = group
                    creatingParent = parent
                    creating = pickingSide
                },
                onBack = { picking = null },
                onHome = onHome
            )
        }
        else -> AppBackground {
            Column(Modifier.fillMaxSize()) {
                TopBar(if (editing) "İşlemi düzenle" else "${type.label} yönetimi", onBack, onHome)
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
                    if (!editing) WhiteBox {
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
                    if (!editing) WhiteBox {
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
                            val cents = parseCents(amountText) ?: 0L
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
                                else -> if (editTxn != null) {
                                    onUpdate(
                                        editTxn.copy(
                                            cents = cents,
                                            date = date.toEpochDay(),
                                            note = note.trim(),
                                            fromId = f.id,
                                            toId = t.id
                                        )
                                    )
                                } else {
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
    parent: Account?,
    accounts: List<Account>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSave: (name: String, group: String, limitCents: Long?, cutoffDay: Int?) -> Unit
) {
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val groups = remember(accounts, type) { accounts.filter { it.type == type }.map { it.groupName }.distinct() }
    val fixedGroup = parent?.groupName ?: initialGroup
    var name by remember { mutableStateOf("") }
    var chosen by remember {
        mutableStateOf(if (fixedGroup.isNotEmpty()) fixedGroup else (groups.firstOrNull() ?: "Genel"))
    }
    var open by remember { mutableStateOf(false) }
    var limitText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isCard = type == AccountType.LIABILITY && chosen == G_CARD && parent == null

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(if (parent != null) "Yeni alt kalem" else "Yeni hesap", onBack, onHome)
            if (parent != null) {
                Breadcrumb("${type.label} › ${trail(parent, byId)} › ${parent.name}")
            } else if (fixedGroup.isNotEmpty()) {
                Breadcrumb("${type.label} › $fixedGroup")
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WhiteBox {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompactField(
                            name, { name = it; error = null },
                            if (parent != null) "Alt kalem adı" else "Hesap adı",
                            flat = true
                        )
                        if (fixedGroup.isEmpty()) {
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
                        if (isCard) {
                            CompactField(limitText, { limitText = it; error = null }, "Kart limiti (TL, isteğe bağlı)", KeyboardType.Decimal, flat = true)
                            CompactField(dayText, { dayText = it; error = null }, "Hesap kesim günü (1-31, isteğe bağlı)", KeyboardType.Number, flat = true)
                        }
                        error?.let { Text(it, color = NegativeRed) }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = typeColor(type)),
                            onClick = {
                                val n = name.trim()
                                val limit = if (isCard && limitText.isNotBlank()) parseCents(limitText) else null
                                val day = if (isCard && dayText.isNotBlank()) dayText.trim().toIntOrNull() else null
                                when {
                                    n.isEmpty() -> error = "Ad girin."
                                    accounts.any {
                                        it.type == type && it.groupName == chosen && it.parentId == parent?.id &&
                                            it.name.equals(n, ignoreCase = true)
                                    } -> error = "Burada aynı adlı bir kalem var."
                                    isCard && limitText.isNotBlank() && (limit == null || limit < 0) -> error = "Geçerli bir limit girin."
                                    isCard && dayText.isNotBlank() && (day == null || day < 1 || day > 31) -> error = "Gün 1 ile 31 arasında olmalı."
                                    else -> onSave(n, chosen, limit, day)
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
fun DaySection(
    title: String,
    color: Color,
    tint: Color,
    list: List<Txn>,
    sign: Int,
    showTotal: Boolean,
    onEdit: (Txn) -> Unit,
    head: (Txn) -> String,
    sub: (Txn) -> String
) {
    if (list.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().background(tint).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$title (${list.size})", Modifier.weight(1f), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (showTotal) Text("Toplam: ${money(list.sumOf { it.cents })}", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Text("Kalem", Modifier.weight(1f), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text("Tutar", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
    list.forEachIndexed { i, t ->
        TxnLine(head(t), sub(t), t.cents, sign, i % 2 == 1, null, { onEdit(t) })
        HorizontalDivider(color = Color(0xFFE3E3E3))
    }
}

@Composable
fun DailyCard(accounts: List<Account>, txns: List<Txn>, onEdit: (Txn) -> Unit) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val dayTxns = remember(txns, day) { txns.filter { it.date == day.toEpochDay() } }
    val incomeL = dayTxns.filter { byId[it.fromId]?.type == AccountType.INCOME }
    val spentL = dayTxns.filter { byId[it.toId]?.type == AccountType.EXPENSE }
    val paidL = dayTxns.filter { byId[it.fromId]?.type == AccountType.ASSET && byId[it.toId]?.type == AccountType.LIABILITY }
    val otherL = dayTxns.filter { it !in incomeL && it !in spentL && it !in paidL }
    val title = if (day == LocalDate.now()) "Bugün" else day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", TR))
    val green = Color(0xFF1E8A5E)
    val red = Color(0xFFB3261E)
    val blue = Color(0xFF3F6E80)
    fun note(t: Txn) = if (t.note.isNotBlank()) " · ${t.note}" else ""
    fun nm(id: Long) = byId[id]?.name ?: "?"

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
            StatBox("Gelir", money(incomeL.sumOf { it.cents }), green, Color(0xFFE6F4EC), Modifier.weight(1f))
            StatBox("Harcama", money(spentL.sumOf { it.cents }), red, Color(0xFFF9E6E4), Modifier.weight(1f))
            StatBox("Ödeme", money(paidL.sumOf { it.cents }), blue, Color(0xFFE5EEF2), Modifier.weight(1f))
        }
        if (dayTxns.isEmpty()) {
            Text("Bu günde işlem yok.", Modifier.padding(14.dp), color = Color.Gray, fontSize = 12.sp)
        } else {
            Text("Düzenlemek için bir satıra dokun.", Modifier.padding(horizontal = 12.dp), fontSize = 10.sp, color = Color.Gray)
        }
        DaySection(
            "Gelirler", green, Color(0xFFE6F4EC), incomeL, 1, true, onEdit,
            head = { t -> nm(t.fromId) },
            sub = { t -> (byId[t.fromId]?.let { trail(it, byId) } ?: "") + " → " + nm(t.toId) + note(t) }
        )
        DaySection(
            "Harcamalar", red, Color(0xFFF9E6E4), spentL, -1, true, onEdit,
            head = { t -> nm(t.toId) },
            sub = { t -> (byId[t.toId]?.let { trail(it, byId) } ?: "") + " · " + nm(t.fromId) + note(t) }
        )
        DaySection(
            "Ödemeler", blue, Color(0xFFE5EEF2), paidL, -1, true, onEdit,
            head = { t -> nm(t.toId) },
            sub = { t -> nm(t.fromId) + " hesabından" + note(t) }
        )
        DaySection(
            "Diğer hareketler", Color(0xFF666666), Color(0xFFEFEFEF), otherL, 0, false, onEdit,
            head = { t -> nm(t.fromId) + " → " + nm(t.toId) },
            sub = { t -> (if (byId[t.fromId]?.type == AccountType.LIABILITY) "Borçlanma" else "Hesaplar arası") + note(t) }
        )
        Spacer(Modifier.height(4.dp))
    }
}
