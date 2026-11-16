package com.karelisio.allo.core

import java.text.Normalizer

/** Recherche T9 : correspondance sur le nom (début de mot ou initiales) et sur le numéro. */
object T9 {
    private val map: Map<Char, Char> = buildMap {
        "abc".forEach { put(it, '2') }
        "def".forEach { put(it, '3') }
        "ghi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }
        "mno".forEach { put(it, '6') }
        "pqrs".forEach { put(it, '7') }
        "tuv".forEach { put(it, '8') }
        "wxyz".forEach { put(it, '9') }
    }

    val letters: Map<Char, String> = mapOf(
        '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
        '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+", '1' to "",
    )

    fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Chiffre T9 d'un caractère, ou null si le caractère n'est pas une lettre/chiffre. */
    fun digitOf(c: Char): Char? {
        if (c.isDigit()) return c
        val base = stripAccents(c.toString()).lowercase().firstOrNull() ?: return null
        return map[base]
    }

    fun toDigits(name: String): String = buildString { name.forEach { c -> digitOf(c)?.let { append(it) } } }

    data class Match(val ranges: List<IntRange>, val score: Int)

    /**
     * Correspondance sur un nom. Renvoie les plages de caractères à surligner.
     *  - préfixe d'un mot (« 526 » → « Jean »), score élevé si c'est le premier mot ;
     *  - initiales (« 53 » → « Jean Dupont ») ;
     *  - préfixe continu sur plusieurs mots (« 5263 » → « Jean Dupont » = « jean d »).
     */
    fun matchName(name: String, query: String): Match? {
        val q = query.filter { it.isDigit() }
        if (q.isEmpty() || name.isBlank()) return null
        // Index des caractères significatifs.
        val chars = name.mapIndexedNotNull { i, c -> digitOf(c)?.let { i to it } }
        if (chars.isEmpty()) return null
        val wordStarts = mutableListOf<Int>() // positions dans `chars`
        chars.forEachIndexed { k, (i, _) ->
            if (k == 0 || i - chars[k - 1].first > 1 || !name[i - 1].isLetterOrDigit()) wordStarts += k
        }
        // 1. Préfixe d'un mot, éventuellement continu sur les mots suivants.
        wordStarts.forEachIndexed { wi, start ->
            if (start + q.length <= chars.size) {
                var ok = true
                for (j in q.indices) if (chars[start + j].second != q[j]) { ok = false; break }
                if (ok) {
                    val idx = (start until start + q.length).map { chars[it].first }
                    return Match(toRanges(idx), if (wi == 0) 100 else 80 - wi)
                }
            }
        }
        // 2. Initiales.
        if (q.length >= 2 && q.length <= wordStarts.size) {
            for (offset in 0..wordStarts.size - q.length) {
                var ok = true
                for (j in q.indices) if (chars[wordStarts[offset + j]].second != q[j]) { ok = false; break }
                if (ok) {
                    val idx = (0 until q.length).map { chars[wordStarts[offset + it]].first }
                    return Match(toRanges(idx), 60 - offset)
                }
            }
        }
        return null
    }

    /** Correspondance sur un numéro (tel qu'affiché) : sous-chaîne des chiffres. */
    fun matchNumber(displayNumber: String, query: String): Match? {
        val q = query.filter { it.isDigit() || it == '+' }.removePrefix("+")
        if (q.isEmpty()) return null
        val digits = displayNumber.mapIndexedNotNull { i, c -> if (c.isDigit()) i to c else null }
        val ds = digits.map { it.second }.joinToString("")
        var pos = ds.indexOf(q)
        var score = 40
        if (pos < 0) {
            // Essai avec la forme nationale ↔ internationale (0612… ↔ 33612…).
            val alt = when {
                q.startsWith("0") && ds.startsWith("33") -> "33" + q.substring(1)
                q.startsWith("33") && ds.startsWith("0") -> "0" + q.substring(2)
                else -> null
            }
            if (alt != null) pos = ds.indexOf(alt).also { if (it >= 0) return Match(toRanges((it until it + alt.length).map { k -> digits[k].first }), 45) }
            return null
        }
        if (pos == 0) score = 50
        val idx = (pos until pos + q.length).map { digits[it].first }
        return Match(toRanges(idx), score)
    }

    private fun toRanges(indices: List<Int>): List<IntRange> {
        if (indices.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var s = indices[0]
        var p = indices[0]
        for (i in 1 until indices.size) {
            val v = indices[i]
            if (v == p + 1) p = v else { out += s..p; s = v; p = v }
        }
        out += s..p
        return out
    }
}
