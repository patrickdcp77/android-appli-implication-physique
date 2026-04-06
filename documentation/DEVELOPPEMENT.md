# Document de developpement - Implication physique des judokas

Derniere date de redaction: 2026-04-06
Projet: `android_BLE_judo_3capteurs`
Module principal: `app`

## 1) Objectif du projet
Cette application Android permet de:
- scanner des capteurs BLE,
- connecter jusqu'a 2 capteurs en parallele,
- afficher les mesures en direct (temperature, cardio, acceleration, impact),
- visualiser des graphes compares pour les capteurs selectionnes.

## 2) Ce qui a ete fait ensemble

### A. Identification de la bonne application
- Application cible confirmee: celle qui affiche le titre `ESP32 Multi BLE`.
- Ancienne application ecartee: UI de type `Judo1/Judo2` (projet different).

### B. Ajout d'un graphe "BPM moyen"
But: afficher l'evolution du BPM moyen dans l'onglet `Graphes`.

Changements:
1. Historique etat ViewModel etendu avec une nouvelle serie `cardioAvg`.
2. Alimentation de `cardioAvg` depuis `state.cardioData.bpmAvg`.
3. Anti-duplication des snapshots mise a jour pour inclure `bpmAvg`.
4. Nouvelle carte graphe ajoutee dans `GraphPage`:
   - Titre: `BPM moyen`
   - Unite: ` bpm`

### C. Renommage du titre dans l'application
- Titre TopBar modifie de `ESP32 Multi BLE` vers:
  - `Implication physique des judokas`

### D. Renommage du nom affiche sous l'icone Android
- `app_name` modifie vers:
  - `Implication physique des judokas`
- Le manifest reference deja `@string/app_name`, donc le changement est applique au label app et activity.

## 3) Fichiers modifies

### `app/src/main/java/com/example/android_ble_judo_3capteurs/ui/BleViewModel.kt`
- `DeviceGraphHistory`:
  - ajout `cardioAvg: List<Float>`
- Pipeline d'historique:
  - ajout de `state.cardioData.bpmAvg` dans le snapshot
  - ajout de l'append/trim sur `cardioAvg`
- Nettoyage:
  - suppression d'un import inutilise

### `app/src/main/java/com/example/android_ble_judo_3capteurs/MainActivity.kt`
- `TopAppBar`:
  - titre renomme en `Implication physique des judokas`
- `GraphPage`:
  - ajout d'un `MultiBleGraphCard` pour `BPM moyen`

### `app/src/main/res/values/strings.xml`
- `app_name` renomme en `Implication physique des judokas`

## 4) Verifications realisees

Build Kotlin:
- Commande executee:
  - `./gradlew :app:compileDebugKotlin --no-daemon`
- Resultat: `BUILD SUCCESSFUL`

Assemble APK debug:
- Commande executee:
  - `./gradlew :app:assembleDebug --no-daemon`
- Resultat: `BUILD SUCCESSFUL`

## 5) Comment verifier rapidement dans l'app
1. Lancer l'app module `app`.
2. Verifier le titre haut ecran:
   - `Implication physique des judokas`
3. Aller dans onglet `Graphes`.
4. Connecter au moins 1 BLE.
5. Verifier la presence de la carte:
   - `BPM moyen`

## 6) Prochaines etapes GitHub
1. Depot GitHub final publie:
   - `https://github.com/patrickdcp77/android-appli-implication-physique.git`
2. Branche distante active:
   - `main`
3. Etape suivante recommandee:
   - ajouter un `README.md` de presentation du projet
4. Optionnel:
   - ajouter un tag de version initiale, par exemple `v1.0.0`
5. Optionnel si travail en equipe:
   - ouvrir des pull requests pour les prochaines evolutions

Commandes utiles pour les prochains commits:
```bash
cd /home/patrick/AndroidStudioProjects/_publish_android_ble_judo_3capteurs
git status
git add .
git commit -m "Mise a jour documentation"
git push
```

## 7) Notes
- Le workspace source contient d'autres dossiers Android, mais le depot Git publie pour cette application est:
  - `/home/patrick/AndroidStudioProjects/_publish_android_ble_judo_3capteurs`
- Le repository GitHub officiel de cette application est:
  - `https://github.com/patrickdcp77/android-appli-implication-physique.git`
- Le module de travail est:
  - `:app`

## 8) Point d'arret de session
- Etat global: stable, push GitHub operationnel via SSH.
- Remote actif:
  - `origin = git@github.com:patrickdcp77/android-appli-implication-physique.git`
- Branche active:
  - `main`
- Commit de cloture:
  - ce commit de documentation "Point d'arret de session" sur `main`
- Documentation disponible:
  - `documentation/DEVELOPPEMENT.md`
  - `documentation/images/`
- Reprise conseillee (prochaine session):
  1. ajouter un `documentation/README.md` pour indexer les captures,
  2. verifier/ranger les noms des images,
  3. continuer les evolutions de l'app puis commit/push sur `main`.

