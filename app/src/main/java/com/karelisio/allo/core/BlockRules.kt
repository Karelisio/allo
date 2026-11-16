package com.karelisio.allo.core

import kotlinx.serialization.Serializable

@Serializable
enum class BlockAction { BLOCK, SILENCE, ALLOW }

@Serializable
enum class BlockCategory { TELEMARKETING, HIDDEN, UNKNOWN, BLACKLIST }

@Serializable
data class BlockConfig(
    val telemarketing: BlockAction = BlockAction.BLOCK,
    val hidden: BlockAction = BlockAction.ALLOW,
    val unknown: BlockAction = BlockAction.ALLOW,
    val blacklist: BlockAction = BlockAction.BLOCK,
) {
    fun actionFor(c: BlockCategory) = when (c) {
        BlockCategory.TELEMARKETING -> telemarketing
        BlockCategory.HIDDEN -> hidden
        BlockCategory.UNKNOWN -> unknown
        BlockCategory.BLACKLIST -> blacklist
    }
}

/** Entrée de liste (noire ou blanche) : numéro exact ou préfixe. */
data class ListEntry(val pattern: String, val isPrefix: Boolean) {
    fun matches(number: String?): Boolean =
        if (isPrefix) PhoneNumbers.matchesPrefix(number, pattern) else PhoneNumbers.same(number, pattern)
}

data class BlockDecision(val action: BlockAction, val category: BlockCategory?) {
    val label: String?
        get() = when {
            action == BlockAction.ALLOW && category == null -> null
            category == BlockCategory.TELEMARKETING -> "Démarchage probable"
            category == BlockCategory.HIDDEN -> "Numéro masqué"
            category == BlockCategory.UNKNOWN -> "Numéro inconnu"
            category == BlockCategory.BLACKLIST -> "Numéro bloqué"
            else -> null
        }

    companion object { val ALLOW = BlockDecision(BlockAction.ALLOW, null) }
}

object BlockRules {
    /** Préfixes ARCEP réservés au démarchage téléphonique (depuis 2023). */
    val DEFAULT_TELEMARKETING_PREFIXES = listOf(
        "0162", "0163", "0270", "0271", "0377", "0378",
        "0424", "0425", "0568", "0569", "0948", "0949",
    )

    /**
     * Ordre : liste blanche → numéro masqué → liste noire → préfixes de démarchage → inconnu.
     * Un contact n'est jamais traité comme « inconnu » ni comme démarchage, mais la liste noire
     * s'applique même aux contacts.
     */
    fun decide(
        number: String?,
        isContact: Boolean,
        whitelist: List<ListEntry>,
        blacklist: List<ListEntry>,
        telemarketingPrefixes: List<String>,
        config: BlockConfig,
    ): BlockDecision {
        if (!PhoneNumbers.isHidden(number) && whitelist.any { it.matches(number) }) return BlockDecision.ALLOW
        if (PhoneNumbers.isHidden(number)) return BlockDecision(config.hidden, BlockCategory.HIDDEN)
        if (blacklist.any { it.matches(number) }) return BlockDecision(config.blacklist, BlockCategory.BLACKLIST)
        if (isContact) return BlockDecision.ALLOW
        if (telemarketingPrefixes.any { PhoneNumbers.matchesPrefix(number, it) }) {
            return BlockDecision(config.telemarketing, BlockCategory.TELEMARKETING)
        }
        return BlockDecision(config.unknown, BlockCategory.UNKNOWN)
    }
}
