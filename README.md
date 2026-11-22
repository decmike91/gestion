# Gestion

Application Android de **gestion des fichiers téléchargés** (audio, vidéo, PDF, images, documents…) avec un **lecteur audio personnalisé** selon l'auditeur et le système d'écoute.

## Fonctions

### Fichiers
- Analyse des dossiers du téléphone (Download, Music, Movies, Documents, DCIM… ou tout le stockage) et des **drives connectés**.
- **Recherche** instantanée (nom, dossier, plusieurs mots).
- **Filtres** : type (audio, vidéo, image, PDF, document, archive, APK), source, taille, date.
- **Tri** : nom, date, taille, type, dossier (croissant / décroissant).
- **Classement** par type, dossier, mois, source ou extension.
- **Aperçu** intégré : images, PDF page par page, vidéos, audio (pochette + métadonnées), fichiers texte (txt, csv, json, md, srt…).
- Ouvrir avec, partager, supprimer.

### Doublons
- **Contenu identique** : même taille puis empreinte SHA-256 (partielle puis complète) → doublons certains.
- **Même nom et taille** (rapide) ou **nom similaire** (ignore « (1) », « copie », « - Copy »…).
- Sélection automatique « garder le plus récent / le plus ancien », espace récupérable, suppression groupée.

### Drives
- Connexion de dossiers via le sélecteur Android (Storage Access Framework) : **OneDrive, Dropbox, Nextcloud, Box, pCloud, MEGA, clé USB, carte SD, partages réseau** (selon les applis installées). L'accès est mémorisé et révocable.
- **Google Drive** : Android n'expose pas de dossier entier ; l'import copie les fichiers choisis dans `Download/Gestion`.

### Lecteur audio
- Lecture en arrière-plan (notification, écran verrouillé, commandes Bluetooth), pause au débranchement du casque.
- Bibliothèque par **morceaux, artistes (chanteurs), albums**, recherche par titre / chanteur / album / genre.
- **Playlists** : création, ajout multiple, réorganisation, tri, lecture aléatoire, répétition.
- **Playlists programmables** : heure, jours (semaine, week-end, une fois…), auditeur, ambiance, volume, arrêt automatique ; reprogrammées après redémarrage.
- Minuterie de sommeil.

### Profils audio
- **Auditeurs** : chacun a son **audiogramme** (droite / gauche, 250 Hz → 8 kHz), saisi depuis un bilan ou **créé par le test auditif intégré** (sons purs pulsés, méthode ascendante). Réglages : intensité de correction, graves, aigus, gain maximal, balance.
- **Systèmes d'écoute** : haut-parleur, écouteurs filaires, casque Bluetooth, voiture, USB… avec leur propre correction ; **sélection automatique** selon la sortie détectée (et association par nom d'appareil Bluetooth, ex. l'autoradio).
- **Ambiances** : Neutre, Concert, Club, Salon cosy, Voix/Podcast, Classique, Jazz, Rock, Électro, Nuit, Détente + ambiances personnalisées (égaliseur 10 bandes, spatialisation, compression).
- Moteur : `DynamicsProcessing` (égaliseur 10 bandes **séparé par oreille**, compression multibande, limiteur anti-saturation). Correction auditive selon la règle du demi-gain.

> Le test et la correction auditive sont indicatifs et ne remplacent pas un audioprothésiste.

## Installation

Chaque push sur `main` compile l'APK via GitHub Actions :
**Actions → Build APK → dernier run → Artifacts → `gestion-apk`**, puis installez `app-debug.apk` sur le téléphone (autoriser les sources inconnues).

Au premier lancement, accordez « Accès à tous les fichiers » (nécessaire pour lister PDF et documents du dossier Téléchargements) et les notifications.

## Compilation locale

Android Studio (Ladybug ou plus récent) ou :

```bash
./gradlew assembleDebug
```

Android 9 (API 28) minimum · Kotlin · Jetpack Compose · Media3/ExoPlayer.

## Structure

```
app/src/main/java/com/decmike/gestion/
├── data/       analyse des fichiers, drives, doublons, filtres
├── audio/      lecteur, profils, audiogramme, test auditif, moteur d'égalisation
├── schedule/   programmation des playlists (AlarmManager)
└── ui/         écrans Compose
```
