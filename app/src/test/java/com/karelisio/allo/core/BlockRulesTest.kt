package com.karelisio.allo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockRulesTest {
    private val prefixes = BlockRules.DEFAULT_TELEMARKETING_PREFIXES

    private fun decide(
        number: String?,
        contact: Boolean = false,
        white: List<ListEntry> = emptyList(),
        black: List<ListEntry> = emptyList(),
        config: BlockConfig = BlockConfig(),
    ) = BlockRules.decide(number, contact, white, black, prefixes, config)

    @Test fun telemarketingPrefixesBlockedByDefault() {
        listOf("0162123456", "+33163123456", "02 70 11 22 33", "0948000000", "+33569123456").forEach {
            val d = decide(it)
            assertEquals(it, BlockAction.BLOCK, d.action)
            assertEquals(BlockCategory.TELEMARKETING, d.category)
        }
    }

    @Test fun regularNumberAllowed() {
        assertEquals(BlockAction.ALLOW, decide("0612345678").action)
    }

    @Test fun telemarketingCanBeSilenced() {
        val d = decide("0162123456", config = BlockConfig(telemarketing = BlockAction.SILENCE))
        assertEquals(BlockAction.SILENCE, d.action)
        assertEquals("Démarchage probable", d.label)
    }

    @Test fun contactsAreNeverTelemarketing() {
        assertEquals(BlockAction.ALLOW, decide("0162123456", contact = true).action)
    }

    @Test fun whitelistWins() {
        val d = decide("0162123456", white = listOf(ListEntry("+33162123456", false)))
        assertEquals(BlockAction.ALLOW, d.action)
    }

    @Test fun blacklistAppliesToContacts() {
        val d = decide("0612345678", contact = true, black = listOf(ListEntry("+33612345678", false)))
        assertEquals(BlockAction.BLOCK, d.action)
        assertEquals(BlockCategory.BLACKLIST, d.category)
    }

    @Test fun blacklistPrefix() {
        val d = decide("0899123456", black = listOf(ListEntry("0899", true)))
        assertEquals(BlockAction.BLOCK, d.action)
    }

    @Test fun hiddenNumbersFollowConfig() {
        assertEquals(BlockAction.ALLOW, decide(null).action)
        assertEquals(BlockAction.BLOCK, decide("", config = BlockConfig(hidden = BlockAction.BLOCK)).action)
        assertEquals(BlockCategory.HIDDEN, decide(null).category)
    }

    @Test fun unknownNumbersFollowConfig() {
        val d = decide("0712345678", config = BlockConfig(unknown = BlockAction.SILENCE))
        assertEquals(BlockAction.SILENCE, d.action)
        assertEquals(BlockCategory.UNKNOWN, d.category)
        assertEquals(BlockAction.ALLOW, decide("0712345678", contact = true, config = BlockConfig(unknown = BlockAction.BLOCK)).action)
    }
}
