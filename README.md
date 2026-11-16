# Allô

Appli Téléphone Android native qui remplace celle de MagicOS (Honor) : clavier, journal, contacts, écrans d'appel et blocage du démarchage.
**100 % locale** : sans compte, sans serveur, sans statistiques. Seule la vérification des mises à jour interroge GitHub.

Kotlin · Jetpack Compose · Material 3 Expressive · `com.karelisio.allo` · Android 10+ (minSdk 29, targetSdk 36).

## Fonctionnalités

- **Rôle Téléphone par défaut** (`RoleManager.ROLE_DIALER`) : demandé à l'onboarding et vérifié au retour. Si le rôle est retiré, un bandeau propose de le reprendre.
- **Appels** : `InCallService` pour les écrans d'appel, `TelecomManager.placeCall` pour appeler, intents `ACTION_DIAL`, `ACTION_CALL` et `tel:`.
- **Double SIM** : choix à chaque appel ou SIM par défaut, SIM préférée par contact, pastille de la SIM dans le journal.
- **Clavier vivant** : touches qui rebondissent et passent du cercle à la fleur, numéro qui apparaît chiffre par chiffre, recherche T9 (nom et numéro, lettres surlignées) avec résultats en cascade. Coller un numéro, appui long sur 0 pour le `+` et sur 1 pour la messagerie, codes spéciaux transmis au système, vibration et sons DTMF réglables.
- **Journal** : groupé par jour (« Aujourd'hui », « Hier »…) et par appels consécutifs, avec durée et SIM. Filtres (tous, manqués, entrants, sortants, bloqués) et recherche. Glisser à droite pour rappeler, à gauche pour un SMS. Appui long : copier, bloquer, ajouter aux contacts, supprimer. Détail d'un numéro avec l'historique complet et la durée totale.
- **Contacts** (via `ContactsContract`, sans base parallèle) : favoris en grille, liste alphabétique avec index de défilement animé, fiche contact animée (actions rapides, sonnerie personnalisée, SIM préférée, blocage, historique), création et modification (photo, numéros, e-mails, note).
- **Appel entrant** : plein écran sur l'écran de verrouillage. Ondes animées, glisser vers le haut ou vers le bas (gros boutons en alternative), réponse SMS modifiable, « Rappelle-moi dans 10 min » (alarme exacte). Notification heads-up quand l'écran est déverrouillé (réglable), et option pour retourner le téléphone afin de couper la sonnerie.
- **Appel en cours** : compteur animé, haut-parleur, muet, clavier DTMF, attente, ajout d'appel, double appel (basculer, fusionner), choix de la sortie audio avec le nom de l'appareil Bluetooth ou de la voiture. Capteur de proximité, et notification CallStyle (Raccrocher, Haut-parleur, Muet).
- **Blocage du démarchage** (`CallScreeningService`) :
  - Préfixes ARCEP (0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949), au format national et +33. La liste est modifiable.
  - Pour chaque catégorie (démarchage, masqués, inconnus, liste noire) : bloquer, sonnerie silencieuse avec étiquette, ou laisser passer.
  - Liste noire et liste blanche, journal des appels filtrés et compteur du mois.
- **Notifications** : appels manqués groupés (Rappeler, SMS, Bloquer), notification discrète à chaque blocage, rappels.
- **Widget Glance** de favoris (2x2 à 4x4, couleurs dynamiques) et **raccourcis d'appli** vers les 4 premiers favoris.
- **Intégrations** :
  - Bulle : les notifications d'appel restent des CallStyle standards, et une option laisse Bulle gérer l'appel entrant quand l'écran est déverrouillé.
  - Écho : bouton « Pense à toi » sur les contacts choisis, voir ci-dessous.
- **Sauvegarde** : export et import JSON des réglages et des listes.
- **Onboarding animé** avec une page par autorisation, plus les écrans **Autorisations** et **Fiabilité** (test d'appel simulé, état du rôle, du blocage et de MagicOS).
- **Mise à jour in-app** depuis les GitHub Releases.

## Architecture

```
app/src/main/java/com/karelisio/allo/
├── core/            Logique pure, testée : PhoneNumbers (+33/0), T9, BlockRules, CallLogGrouping
├── data/            Room (listes, préfixes, appels bloqués, réponses, réglages par contact),
│                    DataStore (préférences), CallLog, ContactsContract, SIM, sauvegarde JSON
├── telecom/         AlloInCallService, CallManager (état des appels en StateFlow),
│                    AlloCallScreeningService, Dialer (placeCall, SIM), proximité, retournement
├── notifications/   Canaux, CallStyle, appels manqués, bloqués, rappels, récepteurs d'actions
├── ui/
│   ├── call/        InCallActivity, CallScreen (entrant → en cours), ondes, fin d'appel en pilule
│   ├── dialer/      Clavier vivant et T9
│   ├── log/         Journal et détail d'un numéro
│   ├── contacts/    Favoris, liste, fiche, édition
│   ├── settings/    Réglages, blocage, réponses rapides, autorisations, fiabilité, Écho, à propos
│   ├── onboarding/  Pager animé et autorisations
│   ├── components/  Avatars MaterialShapes, formes morphées, clavier, sélection de SIM, cascade
│   └── theme/       Material 3 Expressive, couleurs dynamiques (repli vert), réduction des animations
├── widget/          Widget Glance, raccourcis, rendu bitmap des avatars
├── integrations/    Bulle, Écho
└── update/          Vérification GitHub, téléchargement, PackageInstaller
```

Pas d'injection de dépendances externe : un petit `Graph` sert de localisateur. Le journal et les contacts sont lus en direct, avec des `ContentObserver` exposés en Flow.

## Étapes manuelles

### Sur le téléphone (Honor / MagicOS)

1. Installer l'APK, ouvrir Allô et suivre l'onboarding.
2. **Appli Téléphone par défaut** : accepter la demande. Pour la reprendre plus tard : *Paramètres → Applications → Applications par défaut → Appels*, ou le bandeau dans Allô.
3. **Batterie** : autoriser l'exclusion de l'optimisation.
4. **Lancement d'applis** (*Paramètres → Batterie → Lancement d'applis*) : passer Allô en **manuel**, puis activer *démarrage auto*, *lancement secondaire* et *exécution en arrière-plan*.
5. **Autorisations d'Allô** (*Paramètres → Applications → Allô → Autorisations*) : activer *Afficher sur l'écran de verrouillage* et *Fenêtres pop-up en arrière-plan*.
6. Vérifier dans **Réglages → Fiabilité**, puis lancer le **test d'appel entrant**.

MagicOS ne permet pas de vérifier automatiquement les étapes 4 et 5 : Allô te demande de confirmer au retour du réglage.

### Écho

Le bouton « Pense à toi » envoie un intent explicite vers `com.karelisio.echo` :
action `com.karelisio.echo.action.THINKING_OF_YOU`, extras `contact_name`, `contact_lookup_key`, `phone_number`, `source`.
Si Écho ne déclare pas cette action, son activité principale est ouverte avec les mêmes extras.

### Secrets GitHub (signature)

Toujours le **même keystore**, sinon les mises à jour ne s'installent pas par-dessus. À générer une seule fois :

```bash
keytool -genkeypair -v -keystore allo-release.jks -alias allo \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 allo-release.jks > allo-release.jks.b64
```

Dans *Settings → Secrets and variables → Actions* du dépôt :

| Secret | Valeur |
|---|---|
| `ALLO_KEYSTORE_BASE64` | contenu de `allo-release.jks.b64` |
| `ALLO_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ALLO_KEY_ALIAS` | `allo` |
| `ALLO_KEY_PASSWORD` | mot de passe de la clé |

Garde le `.jks` hors du dépôt, en lieu sûr : le `.gitignore` exclut `*.jks`, `*.keystore` et `*.apk`.

## Publier une version

1. Modifier `VERSION` (par ex. `0.2.0`) et ajouter une section `## [0.2.0]` dans `CHANGELOG.md`.
2. Pousser sur `main`.

La CI (`.github/workflows/android.yml`) lance alors les tests et construit l'APK signé.
Si le tag `v0.2.0` n'existe pas encore, elle crée le tag et la GitHub Release `Allô 0.2.0` avec `Allo-v0.2.0.apk` et la section du changelog.
Si le tag existe déjà, elle fait seulement un build de vérification.

`versionName` vient de `VERSION` et `versionCode` vaut `github.run_number`.
L'appli vérifie `releases/latest` au lancement, au plus toutes les 6 h. Elle propose ensuite la mise à jour dans une bottom sheet avec le changelog et l'installe via `PackageInstaller`, en demandant l'autorisation « sources inconnues » à ce moment-là.

## Développement

```bash
./gradlew testDebugUnitTest   # T9, numéros, règles de blocage, regroupement, rendu des écrans d'appel
./gradlew assembleDebug       # APK debug (suffixe .debug, installable à côté de la release)
```

Le build demande le JDK 17+ et le SDK Android 37.
