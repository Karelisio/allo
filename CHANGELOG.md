# Changelog

Toutes les versions d'Allô. La section correspondant au contenu de `VERSION` sert de notes de release.

## [0.7.1] - 2026-11-17

- Fond d'écran d'appel par personne : sur la fiche d'un contact, « Fond d'écran d'appel » → choisis une image, affichée en plein écran pendant ses appels.
- Bluetooth / voiture : quand tu choisis l'écouteur ou le haut-parleur, Allô garde ce choix pendant tout l'appel et le réimpose si la voiture reprend l'audio toute seule.
- Diagnostic « pas de son » : au décroché (et 3 s après), le journal note la sortie réelle, le micro, le mode audio, le volume d'appel et l'appareil utilisé.

## [0.7.0] - 2026-11-17

Fiabilité :
- Audio Android 14+ : nouvelle API CallEndpoint (sortie audio, nom du casque ou de la voiture), ancienne API conservée pour Android 10 à 13.
- Journal de diagnostic local : les derniers événements d'appel (sonnerie, décroché, fin et raison, filtrage, sortie audio), numéros masqués, à copier depuis Fiabilité.
- Fiabilité : tests de la sonnerie, de l'appel avec téléphone verrouillé (appel de test 5 s plus tard) et des règles de blocage, plus l'état des notifications d'appel entrant.

Fonctions :
- Numéros inconnus décrits hors ligne : mobile, fixe et région, box, gratuit, surtaxé, démarchage, pays.
- « Signaler comme démarchage » depuis le journal et le détail d'un numéro.
- Enregistrer en contact un numéro bloqué, avec la note « Démarcheur ».
- Tuile de réglages rapides « Inconnus en silence ».
- Widget des appels récents (manqués en évidence), un toucher pour rappeler.

Finition :
- Les onglets glissent dans le sens de la navigation ; le nom du contact passe en douceur de la liste à la fiche.
- Rebond au toucher sur le journal, les contacts et les favoris ; étoile des favoris animée.
- Icône à thème redessinée : bulle pleine, combiné et ondes en creux.

## [0.6.1] - 2026-11-17

- Messagerie vocale : le numéro renseigné dans Réglages → Appels (ex. 660) est désormais prioritaire sur celui fourni par la SIM, qui pouvait être le numéro « depuis l'étranger ». Champ vide = numéro de la SIM.

## [0.6.0] - 2026-11-16

Confort.

- Statistiques locales : appels, temps au téléphone, entrants / sortants / manqués, appels par jour de la semaine, personnes avec qui tu passes le plus de temps (ce mois, 30 jours, année, tout).
- Sonnerie par groupe de contacts (famille, travail…).
- Écran d'appel : sombre (par défaut) ou aux couleurs du système.
- README : nouveautés et limites connues (appels vidéo, vibration par contact).

## [0.5.0] - 2026-11-16

Qualité.

- Tests de rendu de tous les écrans (réglages, blocage, autorisations, fiabilité, messagerie, clavier, onboarding, en clair et en sombre) : 61 tests.
- Plus aucun avertissement de compilation (API Material 3 centralisées, icônes AutoMirrored).
- Rappel : chaque release passe d'abord le test sur émulateur (démarrage, écrans, appels simulés).

## [0.4.0] - 2026-11-16

Beauté.

- Typographie expressive : police variable Roboto Flex (OFL) pour les chiffres du clavier, le numéro composé, la durée d'appel, le nom de l'appelant et les titres.
- Écran d'appel : entrée animée de la fenêtre et « pop » de l'avatar au ressort.
- Captures d'écran automatiques des écrans clés à chaque build (artefact CI), pour repérer les régressions visuelles.

## [0.3.0] - 2026-11-16

Nouvelles fonctions.

- Journal : sélection multiple (appui long → Sélectionner) et suppression groupée.
- Journal : recherches récentes proposées sous la barre de recherche.
- Messagerie vocale visuelle : écoute, transcription, rappel et suppression des messages fournis au système par l'opérateur ; sinon, bouton pour appeler la messagerie.
- Nouveau contact : choix du compte de destination (Google, Exchange… ou téléphone).
- États vides illustrés et animés (journal, favoris, contacts).

## [0.2.0] - 2026-11-16

Fiabilité.

- Les rappels « Rappelle-moi dans… » survivent au redémarrage du téléphone (reprogrammés au démarrage).
- Appels manqués : notification de secours si le système ne prévient pas Allô.
- Ajouter un appel : le clavier se referme après l'appel et l'écran d'appel revient.
- Nouveau test automatique sur émulateur avant chaque release : démarrage de l'APK final, parcours des écrans, appel entrant simulé (déverrouillé et verrouillé), captures d'écran. La release est bloquée au moindre crash.

## [0.1.1] - 2026-11-16

- Correction du crash au lancement après l'onboarding (affichage des heures du journal).
- Lisibilité : les titres de l'onboarding restaient noirs en mode sombre.
- MagicOS : « Écran de verrouillage » et « Pop-up en arrière-plan » deviennent facultatifs, avec un choix « Option introuvable ».
- Raccourcis d'appli plus robustes.
- Rapport de crash local : en cas d'arrêt, la trace s'affiche au lancement suivant avec un bouton « Copier ».

## [0.1.0] - 2026-11-16

Première version.

- Appli Téléphone par défaut (rôle Téléphone, InCallService, appels sortants et entrants, liens `tel:`).
- Écran d'appel entrant plein écran : ondes animées, glisser pour décrocher ou refuser, réponse rapide par SMS, « Rappelle-moi dans 10 min ».
- Appel en cours : haut-parleur, muet, clavier DTMF, attente, double appel, conférence, sortie audio (Bluetooth, voiture), capteur de proximité, notification CallStyle.
- Clavier vivant avec recherche T9 dans les contacts et le journal.
- Journal groupé par jour et par contact, filtres, gestes de balayage, détail d'un numéro.
- Contacts : favoris, liste alphabétique avec index animé, fiche contact, création et modification.
- Blocage du démarchage : préfixes ARCEP, numéros masqués, inconnus, liste noire et blanche, journal des appels filtrés.
- Double SIM, appels manqués groupés, widget de favoris, raccourcis d'appli.
- Intégrations Bulle et Écho.
- Onboarding animé avec toutes les autorisations (dont MagicOS), écrans Autorisations et Fiabilité.
- Mise à jour in-app depuis GitHub Releases.
