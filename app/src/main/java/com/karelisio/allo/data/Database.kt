package com.karelisio.allo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "blacklist")
data class BlacklistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val isPrefix: Boolean = false,
    val label: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "whitelist")
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val isPrefix: Boolean = false,
    val label: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "prefixes")
data class TelemarketingPrefix(
    @PrimaryKey val prefix: String,
    val enabled: Boolean = true,
)

@Serializable
@Entity(tableName = "blocked_calls")
data class BlockedCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val date: Long,
    val category: String,
    val action: String,
)

@Serializable
@Entity(tableName = "quick_replies")
data class QuickReply(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
)

@Serializable
@Entity(tableName = "contact_settings")
data class ContactSetting(
    @PrimaryKey val lookupKey: String,
    val preferredAccountId: String? = null,
)

@Dao
interface BlockDao {
    @Query("SELECT * FROM blacklist ORDER BY addedAt DESC") fun blacklist(): Flow<List<BlacklistEntry>>
    @Query("SELECT * FROM blacklist") suspend fun blacklistNow(): List<BlacklistEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addBlack(e: BlacklistEntry)
    @Query("DELETE FROM blacklist WHERE id = :id") suspend fun removeBlack(id: Long)
    @Query("DELETE FROM blacklist") suspend fun clearBlack()

    @Query("SELECT * FROM whitelist ORDER BY addedAt DESC") fun whitelist(): Flow<List<WhitelistEntry>>
    @Query("SELECT * FROM whitelist") suspend fun whitelistNow(): List<WhitelistEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addWhite(e: WhitelistEntry)
    @Query("DELETE FROM whitelist WHERE id = :id") suspend fun removeWhite(id: Long)
    @Query("DELETE FROM whitelist") suspend fun clearWhite()

    @Query("SELECT * FROM prefixes ORDER BY prefix") fun prefixes(): Flow<List<TelemarketingPrefix>>
    @Query("SELECT * FROM prefixes") suspend fun prefixesNow(): List<TelemarketingPrefix>
    @Query("SELECT COUNT(*) FROM prefixes") suspend fun prefixCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPrefix(p: TelemarketingPrefix)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPrefixes(p: List<TelemarketingPrefix>)
    @Query("DELETE FROM prefixes WHERE prefix = :p") suspend fun removePrefix(p: String)
    @Query("DELETE FROM prefixes") suspend fun clearPrefixes()

    @Query("SELECT * FROM blocked_calls ORDER BY date DESC") fun blockedCalls(): Flow<List<BlockedCall>>
    @Query("SELECT * FROM blocked_calls ORDER BY date DESC") suspend fun blockedCallsNow(): List<BlockedCall>
    @Query("SELECT COUNT(*) FROM blocked_calls WHERE date >= :since") fun blockedSince(since: Long): Flow<Int>
    @Insert suspend fun logBlocked(c: BlockedCall)
    @Query("DELETE FROM blocked_calls") suspend fun clearBlocked()
    @Query("DELETE FROM blocked_calls WHERE id = :id") suspend fun removeBlocked(id: Long)
}

@Dao
interface MiscDao {
    @Query("SELECT * FROM quick_replies ORDER BY position") fun replies(): Flow<List<QuickReply>>
    @Query("SELECT * FROM quick_replies ORDER BY position") suspend fun repliesNow(): List<QuickReply>
    @Query("SELECT COUNT(*) FROM quick_replies") suspend fun replyCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReply(r: QuickReply)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReplies(r: List<QuickReply>)
    @Query("DELETE FROM quick_replies WHERE id = :id") suspend fun removeReply(id: Long)
    @Query("DELETE FROM quick_replies") suspend fun clearReplies()

    @Query("SELECT * FROM contact_settings WHERE lookupKey = :key") suspend fun contactSetting(key: String): ContactSetting?
    @Query("SELECT * FROM contact_settings WHERE lookupKey = :key") fun contactSettingFlow(key: String): Flow<ContactSetting?>
    @Query("SELECT * FROM contact_settings") suspend fun allContactSettings(): List<ContactSetting>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putContactSetting(s: ContactSetting)
    @Query("DELETE FROM contact_settings") suspend fun clearContactSettings()
}

@Database(
    entities = [BlacklistEntry::class, WhitelistEntry::class, TelemarketingPrefix::class, BlockedCall::class, QuickReply::class, ContactSetting::class],
    version = 1,
    exportSchema = true,
)
abstract class AlloDatabase : RoomDatabase() {
    abstract fun block(): BlockDao
    abstract fun misc(): MiscDao

    companion object {
        @Volatile private var instance: AlloDatabase? = null
        fun get(context: Context): AlloDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AlloDatabase::class.java, "allo.db")
                .build().also { instance = it }
        }

        val DEFAULT_REPLIES = listOf(
            "Je ne peux pas répondre, je te rappelle vite.",
            "Je suis en réunion, je te rappelle plus tard.",
            "J'arrive, je suis en route.",
            "Peux-tu m'envoyer un message ?",
        )
    }
}
