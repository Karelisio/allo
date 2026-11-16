# Changelog

Toutes les versions d'Allô. La section correspondant au contenu de `VERSION` sert de notes de release.

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
