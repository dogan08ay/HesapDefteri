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
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val groupName: String,
    val parentId: Long? = null,
    val limitCents: Long? = null,
    val cutoffDay: Int? = null
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

    @Update
    suspend fun updateAccount(a: Account)

    @Delete
    suspend fun deleteAccount(a: Account)

    @Insert
    suspend fun insertTxns(list: List<Txn>)

    @Insert
    suspend fun insertAccounts(list: List<Account>)

    @Insert
    suspend fun insertFavorites(list: List<Favorite>)

    @Update
    suspend fun updateTxn(t: Txn)

    @Insert
    suspend fun insertFavorite(f: Favorite): Long

    @Query("DELETE FROM transactions")
    suspend fun clearTxns()

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Delete
    suspend fun deleteTxn(t: Txn)

    @Delete
    suspend fun deleteFavorite(f: Favorite)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN parentId INTEGER")
        db.execSQL("UPDATE accounts SET groupName = 'Fatura Giderleri' WHERE groupName = 'Faturalar' AND type = 'EXPENSE'")
        db.execSQL("UPDATE accounts SET groupName = 'Diğer' WHERE groupName = 'Diğer giderler' AND type = 'EXPENSE'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN limitCents INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN cutoffDay INTEGER")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (n in listOf("Elektrik", "Su", "Doğalgaz", "İnternet", "Cep telefonu")) {
            db.execSQL(
                "INSERT INTO accounts (name, type, groupName, parentId, limitCents, cutoffDay) " +
                    "SELECT '$n', 'EXPENSE', 'Fatura Giderleri', NULL, NULL, NULL " +
                    "WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE groupName = 'Fatura Giderleri' AND name = '$n') " +
                    "AND EXISTS (SELECT 1 FROM accounts WHERE groupName = 'Fatura Giderleri')"
            )
        }
        db.execSQL(
            "DELETE FROM accounts WHERE name = 'Elektrik, su, doğalgaz' AND groupName = 'Fatura Giderleri' " +
                "AND id NOT IN (SELECT fromId FROM transactions) AND id NOT IN (SELECT toId FROM transactions)"
        )
    }
}

@Database(entities = [Account::class, Txn::class, Favorite::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "hesap.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
