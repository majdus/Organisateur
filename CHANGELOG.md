# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/) ; le `versionCode`
Android est dérivé du nom de version : `majeure * 10000 + mineure * 100 + correctif`.

## [2.5.0] — 2026-08-07

### Ajouté
- **Réglages de l'écran des tâches.** Une roue dentée dans la barre d'outils ouvre une feuille où
  se règlent deux choses : où se pose une nouvelle tâche — en haut ou en bas de la liste — et ce
  qu'il advient d'une tâche cochée — barrée ou supprimée. Deux contrôles segmentés, sans phrase
  explicative : le titre de section porte le contexte.
- Cocher une tâche sous le réglage « supprimée » la retire de la liste avec le Snackbar
  « Annuler » déjà utilisé par le balayage.

### Modifié
- **Une nouvelle tâche se pose désormais en haut de la liste** par défaut, là où elle se posait
  en bas. Le réglage permet de revenir à l'ancien comportement.
- Le commutateur « Tâche terminée » de la feuille d'édition suit le réglage de cochage, comme la
  case de la carte. Renommer une tâche déjà terminée ne la supprime pas pour autant.

### Notes techniques
- Aucun changement de schéma : les deux réglages vivent dans les préférences `organisateur`
  (`task_new_placement`, `task_check_action`), sous une clé stable indépendante du libellé.
- Se poser en tête, c'est prendre un rang plus petit que tous les autres, fût-il négatif
  (`TaskDao.minPosition()`). Les rangs ne valant que les uns par rapport aux autres, rien n'est
  réindexé, et la prochaine réorganisation au doigt remet la numérotation à plat.
- L'annulation d'une tâche cochée-supprimée la réinsère **décochée** : revenir sur un cochage,
  c'est retrouver une tâche à faire.
- Les contrôles segmentés sont des `MaterialButtonToggleGroup` en `singleSelection` et
  `selectionRequired` — un réglage a toujours une valeur. L'écoute est branchée après la pose de
  la valeur lue, sans quoi la sélection initiale réécrirait la préférence qu'elle vient de lire.

## [2.4.0] — 2026-08-07

### Ajouté
- **Notes de type liste.** Une note est désormais du texte ou une liste à cocher, sur le modèle
  de Google Keep. Entrée coupe la ligne au curseur et en ouvre une nouvelle en dessous ; retour
  arrière en début de ligne la recolle à la précédente ; une poignée permet de réordonner au
  doigt. Cocher un élément le barre et l'envoie sous un en-tête « n éléments cochés », repliable
  pour ne garder à l'écran que ce qui reste à faire.
- **Un seul bouton de création, qui se déploie.** Le bouton d'action de l'écran Notes ouvre deux
  choix — nouvelle note, nouvelle liste — sous un voile qui éteint la grille. Il se referme par
  la croix, le voile, le retour, un défilement ou le déplacement d'une tuile.
- **Conversion dans les deux sens.** Le menu de l'éditeur offre « Afficher les cases » et
  « Masquer les cases » : chaque ligne non vide devient un élément, et inversement. Deux autres
  entrées n'apparaissent que s'il y a des éléments cochés : « Tout décocher » et « Supprimer les
  éléments cochés ».
- Les cartes de la grille montrent les premiers éléments d'une liste avec leur case, cochés
  barrés, suivis d'un « + n autres éléments ».

### Notes techniques
- Base de données en **version 7** : `MIGRATION_6_7` ajoute les colonnes `type` et `items` à la
  table des notes, sans toucher aux notes existantes qui restent du texte.
- Les deux corps ne coexistent jamais : convertir transporte le contenu et vide l'autre, comme
  dans Keep. La mise en forme des caractères est donc perdue au passage en liste, une liste n'en
  portant pas.
- L'ordre du JSON de `items` porte la séparation des sections — non cochés d'abord, cochés
  ensuite. Cocher ou décocher réinsère la ligne à la frontière entre les deux.
- La colonne `items` suit le même régime que le corps de texte face à la fenêtre de 2 Mo du
  curseur SQLite : préfixe seul pour l'aperçu de la grille, lecture par tranches dans l'éditeur.
- La validation d'un champ de saisie est délivrée deux fois par le système — à l'appui puis au
  relâchement — et coupait donc la ligne deux fois ; seul l'appui agit désormais. Le retour
  arrière, lui, arrive par trois chemins différents selon le clavier : les trois sont pris en
  compte, y compris celui d'un clavier physique.

## [2.3.0] — 2026-08-06

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

[2.3.0]: https://github.com/majdus/Organisateur/releases/tag/v2.3.0
[2.2.0]: https://github.com/majdus/Organisateur/releases/tag/v2.2.0
[2.1.0]: https://github.com/majdus/Organisateur/releases/tag/v2.1.0
[2.0.0]: https://github.com/majdus/Organisateur/releases/tag/v2.0.0
