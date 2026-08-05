# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/) ; le `versionCode`
Android est dérivé du nom de version : `majeure * 10000 + mineure * 100 + correctif`.

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
