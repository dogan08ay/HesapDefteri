package com.dogan.hesapdefteri

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class AccountType(val label: String) {
    ASSET("Varlık"),
    LIABILITY("Borç"),
    INCOME("Gelir"),
    EXPENSE("Gider")
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val groupName: String
)

/** Çift taraflı kayıt: tutar fromId hesabından toId hesabına akar. Tutar kuruş cinsindendir. */
@Entity(tableName = "transactions")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cents: Long,
    val date: Long, // LocalDate.toEpochDay()
    val note: String,
    val fromId: Long,
    val toId: Long
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cents: Long,
    val note: String,
    val fromId: Long,
    val toId: Long
)

@Dao
interface AppDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun txns(): Flow<List<Txn>>

    @Query("SELECT * FROM favorites ORDER BY id")
    fun favorites(): Flow<List<Favorite>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    @Insert
    suspend fun insertAccount(a: Account): Long

    @Insert
    suspend fun insertTxns(list: List<Txn>)

    @Insert
    suspend fun insertFavorite(f: Favorite): Long

    @Delete
    suspend fun deleteTxn(t: Txn)

    @Delete
    suspend fun deleteFavorite(f: Favorite)
}

@Database(entities = [Account::class, Txn::class, Favorite::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "hesap.db").build()
    }
}
