# Implication physique des judokas (Android / BLE)

Application Android (Jetpack Compose) pour scanner et connecter jusqu'à **2 capteurs BLE** (ESP32), afficher les mesures en direct et tracer des graphes comparatifs.

## Fonctionnalités

- Scan BLE et connexion jusqu'à **2 périphériques**
- Affichage temps réel :
  - Température
  - Cardio (BPM)
  - Accélération globale
  - Niveau d'impact (calculé côté appli)
- Onglet **Graphes** : comparaison des BLE sélectionnés
  - Légende avec **valeur instantanée** sous chaque BLE
- Filtrage anti-bruit pour les graphes :
  - **BPM < 40** → graphe plat à 0
  - **accel < 0.4 g** → graphe plat à 0

## Prérequis

- Android Studio (version récente recommandée)
- Téléphone Android avec Bluetooth LE

## Lancer le projet

1. Ouvrir ce dossier dans Android Studio :
   - `_publish_android_ble_judo_3capteurs/`
2. Synchroniser Gradle
3. Lancer la configuration **app** sur un appareil

## Utilisation

- Onglet **Connexion** :
  1. Autoriser les permissions BLE
  2. Scanner puis connecter 1 ou 2 BLE
  3. Choisir une catégorie d'athlète (ENFANT / DÉBUTANT / CONFIRMÉ)
  4. Ajuster les seuils d'impact si besoin

- Onglet **Graphes** :
  1. Sélectionner les BLE à comparer
  2. Consulter les graphes (Temp, BPM moyen, Accel, Niveau impact)

## Documentation

- Suivi de développement : `documentation/DEVELOPPEMENT.md`
- Captures d'écran :
  - `documentation/copies-ecrans-phase1/`
  - `documentation/copies-ecrans-phase2 calibrage impact/`
  - `documentation/images/`

## Firmware / format BLE

Le format des notifications BLE et les UUID de service/caractéristiques sont décrits dans la documentation du projet firmware (ESP32).

## Licence

À définir.

