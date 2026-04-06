# Document de developpement - Implication physique des judokas

Date de mise a jour: 2026-04-06
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
1. Creer le repository GitHub (vide de preference).
2. Ajouter le remote et pousser `main`.
3. Ajouter un tag de version initiale (optionnel), ex: `v1.0.0`.
4. Ouvrir une PR (si workflow equipe) avec ce document dans la description.

Commandes utiles:
```bash
cd /home/patrick/AndroidStudioProjects
git status
git add .
git commit -m "Ajout graphe BPM moyen et renommage app"
git remote add origin <URL_DU_REPO>
git push -u origin main
```

## 7) Notes
- Le projet peut contenir d'autres dossiers Android dans le workspace; la cible de ce travail est la racine:
  - `/home/patrick/AndroidStudioProjects`
- Le module de travail est:
  - `:app`

