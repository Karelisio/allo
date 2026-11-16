package com.karelisio.allo.data

import com.karelisio.allo.core.BlockAction
import com.karelisio.allo.core.BlockCategory
import com.karelisio.allo.core.BlockDecision
import com.karelisio.allo.core.BlockRules
import com.karelisio.allo.core.ListEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class BlockRepository(
    private val db: AlloDatabase,
    private val settings: SettingsStore,
    private val contacts: ContactsRepository,
) {
    private val dao get() = db.block()

    val blacklist = dao.blacklist()
    val whitelist = dao.whitelist()
    val prefixes = dao.prefixes()
    val blockedCalls = dao.blockedCalls()

    fun blockedThisMonth(): Flow<Int> {
        val start = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.blockedSince(start)
    }

    suspend fun seedDefaults() {
        if (dao.prefixCount() == 0) dao.putPrefixes(BlockRules.DEFAULT_TELEMARKETING_PREFIXES.map { TelemarketingPrefix(it) })
        if (db.misc().replyCount() == 0) {
            db.misc().putReplies(AlloDatabase.DEFAULT_REPLIES.mapIndexed { i, t -> QuickReply(text = t, position = i) })
        }
    }

    suspend fun decide(number: String?): BlockDecision {
        val isContact = contacts.lookup(number) != null
        return BlockRules.decide(
            number = number,
            isContact = isContact,
            whitelist = dao.whitelistNow().map { ListEntry(it.pattern, it.isPrefix) },
            blacklist = dao.blacklistNow().map { ListEntry(it.pattern, it.isPrefix) },
            telemarketingPrefixes = dao.prefixesNow().filter { it.enabled }.map { it.prefix },
            config = settings.current().blockConfig,
        )
    }

    suspend fun isBlacklisted(number: String?): Boolean = dao.blacklistNow().any { ListEntry(it.pattern, it.isPrefix).matches(number) }

    suspend fun block(pattern: String, isPrefix: Boolean = false, label: String? = null) {
        // Un numéro bloqué sort de la liste blanche.
        dao.whitelistNow().filter { ListEntry(it.pattern, it.isPrefix).matches(pattern) && !it.isPrefix }.forEach { dao.removeWhite(it.id) }
        if (dao.blacklistNow().none { it.pattern == pattern && it.isPrefix == isPrefix }) dao.addBlack(BlacklistEntry(pattern = pattern, isPrefix = isPrefix, label = label))
    }

    suspend fun unblock(number: String) {
        dao.blacklistNow().filter { !it.isPrefix && ListEntry(it.pattern, false).matches(number) }.forEach { dao.removeBlack(it.id) }
    }

    suspend fun allow(pattern: String, isPrefix: Boolean = false, label: String? = null) {
        unblock(pattern)
        if (dao.whitelistNow().none { it.pattern == pattern && it.isPrefix == isPrefix }) dao.addWhite(WhitelistEntry(pattern = pattern, isPrefix = isPrefix, label = label))
    }

    suspend fun removeBlack(id: Long) = dao.removeBlack(id)
    suspend fun removeWhite(id: Long) = dao.removeWhite(id)
    suspend fun putPrefix(p: TelemarketingPrefix) = dao.putPrefix(p)
    suspend fun removePrefix(p: String) = dao.removePrefix(p)
    suspend fun resetPrefixes() { dao.clearPrefixes(); dao.putPrefixes(BlockRules.DEFAULT_TELEMARKETING_PREFIXES.map { TelemarketingPrefix(it) }) }
    suspend fun clearBlockedLog() = dao.clearBlocked()
    suspend fun removeBlockedLog(id: Long) = dao.removeBlocked(id)

    suspend fun log(number: String, category: BlockCategory?, action: BlockAction) {
        dao.logBlocked(BlockedCall(number = number, date = System.currentTimeMillis(), category = category?.name ?: "", action = action.name))
    }
}
