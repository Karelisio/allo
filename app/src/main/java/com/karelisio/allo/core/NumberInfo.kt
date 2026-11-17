package com.karelisio.allo.core

/**
 * Description hors ligne d'un numéro, à partir du plan de numérotation français (ARCEP).
 * L'opérateur n'est pas déduit : la portabilité rend le préfixe trompeur.
 */
object NumberInfo {
    private val regions = mapOf(
        '1' to "Île-de-France", '2' to "Nord-Ouest", '3' to "Nord-Est", '4' to "Sud-Est", '5' to "Sud-Ouest",
    )

    private val countries = listOf(
        "1" to "États-Unis / Canada", "7" to "Russie", "20" to "Égypte", "27" to "Afrique du Sud", "30" to "Grèce",
        "31" to "Pays-Bas", "32" to "Belgique", "34" to "Espagne", "351" to "Portugal", "352" to "Luxembourg",
        "353" to "Irlande", "356" to "Malte", "358" to "Finlande", "36" to "Hongrie", "377" to "Monaco",
        "39" to "Italie", "40" to "Roumanie", "41" to "Suisse", "420" to "Tchéquie", "43" to "Autriche",
        "44" to "Royaume-Uni", "45" to "Danemark", "46" to "Suède", "47" to "Norvège", "48" to "Pologne",
        "49" to "Allemagne", "52" to "Mexique", "55" to "Brésil", "61" to "Australie", "81" to "Japon",
        "86" to "Chine", "90" to "Turquie", "91" to "Inde", "212" to "Maroc", "213" to "Algérie",
        "216" to "Tunisie", "221" to "Sénégal", "225" to "Côte d'Ivoire", "237" to "Cameroun",
        "262" to "La Réunion / Mayotte", "590" to "Guadeloupe", "594" to "Guyane", "596" to "Martinique",
        "689" to "Polynésie française", "687" to "Nouvelle-Calédonie", "971" to "Émirats arabes unis", "972" to "Israël",
    ).sortedByDescending { it.first.length }

    /** Libellé court, ou null si rien d'utile (numéro masqué, code spécial). */
    fun describe(raw: String?, telemarketingPrefixes: List<String> = BlockRules.DEFAULT_TELEMARKETING_PREFIXES): String? {
        if (PhoneNumbers.isHidden(raw)) return null
        val n = PhoneNumbers.normalize(raw)
        if (n.contains('*') || n.contains('#')) return null
        if (n.startsWith("+") && !n.startsWith("+33")) {
            val digits = n.drop(1)
            val c = countries.firstOrNull { digits.startsWith(it.first) } ?: return "International"
            return "International · ${c.second}"
        }
        val nat = PhoneNumbers.toNational(n)
        if (nat.length in 2..6 && !nat.startsWith("0")) return "Numéro court"
        if (nat.length != 10 || !nat.startsWith("0")) return null
        if (telemarketingPrefixes.any { PhoneNumbers.matchesPrefix(nat, it) }) return "Démarchage (préfixe officiel)"
        return when (nat[1]) {
            '1', '2', '3', '4', '5' -> "Fixe · ${regions[nat[1]]}"
            '6', '7' -> "Mobile"
            '8' -> when {
                nat.startsWith("080") -> "Numéro gratuit"
                nat.startsWith("081") -> "Numéro à tarif modéré"
                nat.startsWith("082") || nat.startsWith("089") -> "Numéro surtaxé"
                else -> "Numéro spécial"
            }
            '9' -> "Fixe · box internet"
            else -> null
        }
    }
}
