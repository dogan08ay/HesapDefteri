package com.dogan.hesapdefteri

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainVm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.build(app)
    private val dao = db.dao()

    val accounts: StateFlow<List<Account>> =
        dao.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val txns: StateFlow<List<Txn>> =
        dao.txns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites: StateFlow<List<Favorite>> =
        dao.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { if (dao.accountCount() == 0) seed() }
    }

    private suspend fun seed() {
        fun add(type: AccountType, group: String, vararg names: String) = names.map {
            Account(name = it, type = type, groupName = group)
        }
        val all = buildList {
            val a = AccountType.ASSET
            addAll(add(a, "Nakit", "Cüzdan"))
            addAll(add(a, "Banka hesapları", "Vadesiz hesap"))
            addAll(add(a, "Alacaklar", "Alacak"))
            addAll(add(a, "Yatırım fonları", "Fon"))
            addAll(add(a, "Hisse senetleri", "Hisse portföyü"))
            addAll(add(a, "Kıymetli madenler", "Altın"))
            addAll(add(a, "Diğer varlıklar", "Diğer varlık"))
            val l = AccountType.LIABILITY
            addAll(add(l, "Kredi kartları", "Kredi kartı"))
            addAll(add(l, "Krediler", "Banka kredisi"))
            addAll(add(l, "Özel borçlar", "Kişisel borç"))
            addAll(add(l, "Fatura borçları", "Fatura borcu"))
            val i = AccountType.INCOME
            addAll(add(i, "Maaş", "Maaş"))
            addAll(add(i, "Kira geliri", "Kira geliri"))
            addAll(add(i, "Diğer gelirler", "Diğer gelir"))
            val e = AccountType.EXPENSE
            addAll(add(e, "Aidat", "Site aidatı"))
            addAll(add(e, "Eğitim", "Okul ve kurs"))
            addAll(add(e, "Eğlence", "Eğlence"))
            addAll(add(e, "Giyim", "Giyim"))
            addAll(add(e, "Gıda", "Market"))
            addAll(add(e, "Kira", "Ev kirası"))
            addAll(add(e, "Ulaşım", "Yakıt ve ulaşım"))
            addAll(add(e, "Sağlık", "Sağlık"))
            addAll(add(e, "Fatura Giderleri", "Elektrik", "Su", "Doğalgaz", "İnternet", "Cep telefonu"))
            addAll(add(e, "Diğer", "Diğer gider"))
        }
        all.forEach { dao.insertAccount(it) }
    }

    fun addAccount(
        name: String,
        group: String,
        type: AccountType,
        parentId: Long? = null,
        limitCents: Long? = null,
        cutoffDay: Int? = null,
        onDone: (Account) -> Unit = {}
    ) {
        viewModelScope.launch {
            val a = Account(
                name = name, type = type, groupName = group, parentId = parentId,
                limitCents = limitCents, cutoffDay = cutoffDay
            )
            val id = dao.insertAccount(a)
            onDone(a.copy(id = id))
        }
    }

    fun updateAccount(a: Account) {
        viewModelScope.launch { dao.updateAccount(a) }
    }

    fun deleteAccount(a: Account) {
        viewModelScope.launch { dao.deleteAccount(a) }
    }

    fun addTxns(list: List<Txn>, favorite: Favorite?) {
        viewModelScope.launch {
            dao.insertTxns(list)
            if (favorite != null) dao.insertFavorite(favorite)
        }
    }

    fun updateTxn(t: Txn) {
        viewModelScope.launch { dao.updateTxn(t) }
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("v", 1)
        val accs = JSONArray()
        accounts.value.forEach { a ->
            accs.put(
                JSONObject()
                    .put("id", a.id).put("name", a.name).put("type", a.type.name).put("group", a.groupName)
                    .put("parent", a.parentId ?: JSONObject.NULL)
                    .put("limit", a.limitCents ?: JSONObject.NULL)
                    .put("cutoff", a.cutoffDay ?: JSONObject.NULL)
            )
        }
        val txs = JSONArray()
        txns.value.forEach { t ->
            txs.put(
                JSONObject().put("id", t.id).put("cents", t.cents).put("date", t.date)
                    .put("note", t.note).put("from", t.fromId).put("to", t.toId)
            )
        }
        val favs = JSONArray()
        favorites.value.forEach { f ->
            favs.put(
                JSONObject().put("id", f.id).put("name", f.name).put("cents", f.cents)
                    .put("note", f.note).put("from", f.fromId).put("to", f.toId)
            )
        }
        root.put("accounts", accs).put("txns", txs).put("favs", favs)
        return root.toString(2)
    }

    fun importJson(text: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(text)
                val accArr = root.getJSONArray("accounts")
                val txArr = root.getJSONArray("txns")
                val favArr = root.optJSONArray("favs") ?: JSONArray()
                val accs = (0 until accArr.length()).map { i ->
                    val o = accArr.getJSONObject(i)
                    Account(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        type = AccountType.valueOf(o.getString("type")),
                        groupName = o.getString("group"),
                        parentId = if (o.isNull("parent")) null else o.getLong("parent"),
                        limitCents = if (o.isNull("limit")) null else o.getLong("limit"),
                        cutoffDay = if (o.isNull("cutoff")) null else o.getInt("cutoff")
                    )
                }
                val txs = (0 until txArr.length()).map { i ->
                    val o = txArr.getJSONObject(i)
                    Txn(
                        id = o.getLong("id"), cents = o.getLong("cents"), date = o.getLong("date"),
                        note = o.getString("note"), fromId = o.getLong("from"), toId = o.getLong("to")
                    )
                }
                val favs = (0 until favArr.length()).map { i ->
                    val o = favArr.getJSONObject(i)
                    Favorite(
                        id = o.getLong("id"), name = o.getString("name"), cents = o.getLong("cents"),
                        note = o.getString("note"), fromId = o.getLong("from"), toId = o.getLong("to")
                    )
                }
                db.withTransaction {
                    dao.clearTxns()
                    dao.clearFavorites()
                    dao.clearAccounts()
                    dao.insertAccounts(accs)
                    dao.insertTxns(txs)
                    dao.insertFavorites(favs)
                }
                onResult(true, "Yedek geri yüklendi: ${accs.size} hesap, ${txs.size} işlem.")
            } catch (e: Exception) {
                onResult(false, "Yedek okunamadı: ${e.message}")
            }
        }
    }

    fun deleteTxn(t: Txn) {
        viewModelScope.launch { dao.deleteTxn(t) }
    }

    fun resetAll() {
        viewModelScope.launch {
            dao.clearTxns()
            dao.clearFavorites()
            dao.clearAccounts()
            seed()
        }
    }

    fun deleteFavorite(f: Favorite) {
        viewModelScope.launch { dao.deleteFavorite(f) }
    }
}
