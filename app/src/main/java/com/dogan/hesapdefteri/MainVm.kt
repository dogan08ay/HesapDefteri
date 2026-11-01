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

    init {
        viewModelScope.launch { if (dao.accountCount() == 0) seed() }
    }

    private suspend fun seed() {
        val a = AccountType.ASSET
        val l = AccountType.LIABILITY
        val i = AccountType.INCOME
        val e = AccountType.EXPENSE
        listOf(
            Account(name = "Cüzdan", type = a, groupName = "Nakit"),
            Account(name = "Banka hesabı", type = a, groupName = "Banka hesapları"),
            Account(name = "Kredi kartı", type = l, groupName = "Kartlar"),
            Account(name = "Maaş", type = i, groupName = "Düzenli gelir"),
            Account(name = "Market", type = e, groupName = "Günlük"),
            Account(name = "Kira", type = e, groupName = "Sabit giderler"),
            Account(name = "Fatura", type = e, groupName = "Sabit giderler"),
            Account(name = "Ulaşım", type = e, groupName = "Günlük")
        ).forEach { dao.insertAccount(it) }
    }

    fun addAccount(name: String, group: String, type: AccountType) {
        viewModelScope.launch { dao.insertAccount(Account(name = name, type = type, groupName = group)) }
    }

    fun addTxn(cents: Long, date: Long, note: String, fromId: Long, toId: Long) {
        viewModelScope.launch {
            dao.insertTxn(Txn(cents = cents, date = date, note = note, fromId = fromId, toId = toId))
        }
    }

    fun deleteTxn(t: Txn) {
        viewModelScope.launch { dao.deleteTxn(t) }
    }
}
