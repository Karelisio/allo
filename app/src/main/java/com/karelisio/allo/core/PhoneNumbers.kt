package com.karelisio.allo.core

/**
 * Normalisation et formatage des numéros, pensés pour la France (+33 / 0) mais tolérants
 * aux numéros internationaux.
 */
object PhoneNumbers {
    const val FR_CC = "33"

    /** Garde uniquement les chiffres et un éventuel « + » initial. */
    fun clean(raw: String?): String {
        if (raw == null) return ""
        val sb = StringBuilder()
        raw.trim().forEachIndexed { i, c ->
            when {
                c.isDigit() -> sb.append(c)
                c == '+' && sb.isEmpty() -> sb.append('+')
                (c == '*' || c == '#') -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Forme canonique (E.164 quand c'est possible) :
     * « 06 12 34 56 78 » → « +33612345678 », « 0033 6… » → « +336… ».
     * Les numéros courts (15, 3644…) et les codes restent inchangés.
     */
    fun normalize(raw: String?, countryCode: String = FR_CC): String {
        var n = clean(raw)
        if (n.isEmpty()) return ""
        if (n.contains('*') || n.contains('#')) return n
        if (n.startsWith("00")) n = "+" + n.substring(2)
        if (n.startsWith("+")) {
            // +33 0 6… (zéro superflu)
            if (n.startsWith("+${countryCode}0") && n.length == countryCode.length + 11) {
                n = "+$countryCode" + n.substring(countryCode.length + 2)
            }
            return n
        }
        if (countryCode == FR_CC && n.length == 10 && n.startsWith("0") && n[1] != '0') {
            return "+$FR_CC" + n.substring(1)
        }
        return n
    }

    /** Forme nationale française : « +33612345678 » → « 0612345678 ». */
    fun toNational(raw: String?): String {
        val n = normalize(raw)
        return if (n.startsWith("+$FR_CC") && n.length == 12) "0" + n.substring(3) else n.removePrefix("+")
    }

    /** Affichage lisible : « 06 12 34 56 78 », « +44 20 7946 0958 » laissé groupé simplement. */
    fun format(raw: String?): String {
        val n = normalize(raw)
        if (n.isEmpty()) return raw.orEmpty()
        if (n.startsWith("+$FR_CC") && n.length == 12) {
            val nat = "0" + n.substring(3)
            return nat.chunked(2).joinToString(" ")
        }
        if (!n.startsWith("+") && n.length == 10 && n.startsWith("0")) return n.chunked(2).joinToString(" ")
        if (n.startsWith("+") && n.length > 6) {
            val cc = n.substring(1, 3)
            return "+$cc " + n.substring(3).chunked(3).joinToString(" ")
        }
        return n
    }

    /** Deux numéros désignent-ils la même ligne ? Compare la forme canonique, puis les 9 derniers chiffres. */
    fun same(a: String?, b: String?): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val da = na.filter { it.isDigit() }
        val db = nb.filter { it.isDigit() }
        if (da.length < 9 || db.length < 9) return false
        return da.takeLast(9) == db.takeLast(9)
    }

    /** Clé de regroupement stable pour un numéro. */
    fun key(raw: String?): String {
        val n = normalize(raw)
        val d = n.filter { it.isDigit() }
        return if (d.length >= 9) d.takeLast(9) else n
    }

    /**
     * Le numéro commence-t-il par ce préfixe ? Le préfixe peut être donné au format national
     * (« 0162 ») ou international (« +33162 »).
     */
    fun matchesPrefix(number: String?, prefix: String): Boolean {
        val nat = toNational(number)
        val p = toNationalPrefix(prefix)
        if (p.isEmpty() || nat.isEmpty()) return false
        return nat.startsWith(p)
    }

    fun toNationalPrefix(prefix: String): String {
        var p = clean(prefix)
        if (p.startsWith("00")) p = "+" + p.substring(2)
        if (p.startsWith("+$FR_CC")) p = "0" + p.substring(3)
        return p.removePrefix("+")
    }

    /** Numéro masqué / inconnu fourni par le réseau. */
    fun isHidden(raw: String?): Boolean {
        val c = clean(raw)
        return c.isEmpty() || raw == "-1" || raw == "-2" || raw == "-3" ||
            raw.equals("private", true) || raw.equals("unknown", true)
    }

    fun isSpecialCode(raw: String): Boolean {
        val c = raw.trim()
        return c.length >= 3 && (c.startsWith("*") || c.startsWith("#")) && c.endsWith("#")
    }

    /** « *#*#4636#*#* » → « 4636 » (codes secrets transmis via sendDialerSpecialCode). */
    fun secretCode(raw: String): String? {
        val m = Regex("^\\*#\\*#(\\d+)#\\*#\\*$").find(raw.trim()) ?: return null
        return m.groupValues[1]
    }
}
