# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/) ; le `versionCode`
Android est dérivé du nom de version : `majeure * 10000 + mineure * 100 + correctif`.

## [Non publié]

### Ajouté
- **Préparation de la signature.** Le `build.gradle` sait signer la variante release à partir d'un
  `keystore.properties` non versionné. En son absence la compilation aboutit quand même, sur un
  APK non signé : on peut donc construire le projet sans détenir la clé. Le modèle à recopier est
  `keystore.properties.example`.

### Modifié
- **Affichage de bord à bord.** L'application dessine désormais sous les barres système, comme
  l'impose Android 16. Le rendu est inchangé à l'œil : les barres laissent voir le fond de l'écran,
  et le contenu est repoussé de leur hauteur. L'éditeur de notes garde sa teinte jusqu'aux bords.

### Notes techniques
- `targetSdk` et `compileSdk` passent de 34 à **36**, exigence du Play Store pour toute
  soumission à partir du 31 août 2026.
- Chaîne de compilation remise à niveau, imposée par le `compileSdk 36` : AGP 7.4.2 → 8.9.1,
  Gradle 7.5 → 8.11.1, Kotlin 1.8.22 → 2.1.0, Java 8 → 17. Le nom de paquet quitte le manifeste
  pour le `namespace` du `build.gradle`, et `jcenter()` disparaît des dépôts.
- **Room passe de kapt à KSP** et de 2.6.1 à 2.7.1 : kapt ne sait pas lire les métadonnées
  Kotlin 2.1 et faisait échouer la compilation. Aucun changement de schéma, donc aucune migration.
- `statusBarColor` et `navigationBarColor` n'étant plus honorés, les encarts sont appliqués en
  marge intérieure par `padForSystemBars` sur le `rootLayout` des six écrans. L'éditeur de notes
  teinte le fond de fenêtre, seul moyen restant de colorer la zone des barres, et prend aussi en
  compte l'encart du clavier pour garder sa barre de mise en forme visible.
- Le voile de contraste que le système pose sur les barres est désactivé
  (`enforceStatusBarContrast`, `enforceNavigationBarContrast`) : il trancherait sur un fond clair.
- Material 1.12 est requis pour que les `BottomSheetDialog`, où se fait toute l'édition, gèrent
  les encarts.

## [2.2.0] — 2026-08-05

### Ajouté
- **Calendrier hijri.** Un bouton de réglages dans la barre du titre de l'écran Calendrier permet
  de choisir entre calendrier grégorien et hijri. Le grégorien reste le calendrier par défaut, et
  le choix est retenu d'une ouverture à l'autre.
- **Correspondance grégorienne** en mode hijri, à trois niveaux : les mois grégoriens couverts
  sous le titre du mois, un `d/MM` dans chaque case, et la date civile sous l'en-tête du jour
  sélectionné.

### Modifié
- Tuiles de rappel resserrées d'environ un cinquième en hauteur, typographie échelonnée en
  conséquence et rayon des coins ramené à 20dp.

### Notes techniques
- La variante hijri retenue est **Umm al-Qura**, celle qu'affichent par défaut Android, iOS et la
  plupart des applications.
- Le choix de calendrier ne touche que l'affichage : les événements restent rangés sous une clé
  grégorienne `yyyy-MM-dd`, et les requêtes par plage sont inchangées. **Aucune migration de base.**
- Les noms de mois hijri sont définis dans `strings.xml` (tableau `hijri_months`) plutôt que pris
  chez le système, dont rien ne garantit qu'il sache les nommer en français.
- La grille passe de `java.util.Calendar` à `android.icu.util.Calendar` : seule cette famille sait
  compter en mois hijri, et elle est disponible depuis l'API 24 — bien en deçà du `minSdk` 28.

## [2.1.0] — 2026-08-05

### Ajouté
- **Réorganisation des notes par glisser-déposer.** Un appui long soulève une tuile ; les voisines
  s'écartent en direct et la tuile se pose où on la relâche.
- **Réorganisation des tâches par glisser-déposer**, pour ranger par importance. Le déplacement
  reste à l'intérieur des groupes « à faire » et « terminées » : cette frontière relève de la case
  à cocher. Le balayage-suppression est conservé.

### Modifié
- L'ordre des notes et des tâches est désormais entièrement manuel : modifier une note ne la fait
  plus remonter en tête. Une note créée se pose en première position, une tâche créée en fin de
  liste.
- Grille des notes : hauteurs bornées (plancher de 120dp, aperçu à 6 lignes) pour une quinconce
  plus légère, et comblement des trous laissé actif afin qu'aucun creux ne subsiste en haut.
- La suppression d'une note passe désormais par l'éditeur, l'appui long étant pris par le
  déplacement.
- `versionCode` et `versionName` alignés sur les tags Git, qu'ils avaient cessé de suivre.

### Notes techniques
- Migrations Room 4→5 et 5→6 : une colonne `position` sur les notes puis sur les tâches, remplie
  d'après l'ordre affiché jusque-là pour que rien ne bouge à la première ouverture.
- Les adaptateurs concernés quittent `ListAdapter`, qui calcule ses différences en tâche de fond
  et décalait chaque franchissement pendant un glisser.

## [2.0.0] — 2026-07-24

### Ajouté
- Notes multiples, avec titre, corps en texte enrichi et couleur.
- Demande de la permission de notification et icône d'application adaptative.

### Modifié
- Refonte graphique complète sur le thème « Pure Light », étendue aux cinq écrans, avec une teinte
  par fonctionnalité et des boîtes de dialogue sur mesure.
- Refonte de l'écran Rappels, qui sert désormais de référence graphique au reste de l'application.

### Corrigé
- Notes volumineuses : plus de plantage ni de perte de contenu.
- Défilement des notes.

## Avant 2.0.0

Premières versions : tâches, rappels et calendrier, édition des éléments, interrupteur sur les
rappels, jeu d'icônes. Voir l'historique Git pour le détail.

[2.2.0]: https://github.com/majdus/Organisateur/releases/tag/v2.2.0
[2.1.0]: https://github.com/majdus/Organisateur/releases/tag/v2.1.0
[2.0.0]: https://github.com/majdus/Organisateur/releases/tag/v2.0.0
