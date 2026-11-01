package com.dogan.hesapdefteri

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.build(app).dao()

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
            addAll(add(e, "Faturalar", "Elektrik, su, doğalgaz"))
            addAll(add(e, "Diğer giderler", "Diğer gider"))
        }
        all.forEach { dao.insertAccount(it) }
    }

    fun addAccount(name: String, group: String, type: AccountType) {
        viewModelScope.launch { dao.insertAccount(Account(name = name, type = type, groupName = group)) }
    }

    fun addTxns(list: List<Txn>, favorite: Favorite?) {
        viewModelScope.launch {
            dao.insertTxns(list)
            if (favorite != null) dao.insertFavorite(favorite)
        }
    }

    fun deleteTxn(t: Txn) {
        viewModelScope.launch { dao.deleteTxn(t) }
    }

    fun deleteFavorite(f: Favorite) {
        viewModelScope.launch { dao.deleteFavorite(f) }
    }
}
