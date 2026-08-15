# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/) ; le `versionCode`
Android est dérivé du nom de version : `majeure * 10000 + mineure * 100 + correctif`.

## [2.6.1] — 2026-08-15

### Corrigé
- **La frappe est redevenue fluide dans une note volumineuse.** Sur une note de 37 000 caractères
  entièrement en gras, chaque lettre tapée faisait remettre en page tout le texte suivant le
  curseur : 105 ms de retard et près de cinq mégaoctets alloués par frappe, donc une ramasse-miettes
  à chaque touche. La mise en forme qui vaut pour toute la note est désormais portée par le champ
  de saisie lui-même et non par un span, ce qui la rend gratuite. Mesuré après correction : 0,7 ms
  d'attente et 1 ms de dessin par image, contre 105 et 72 auparavant.
- **Une note dont les sauts de ligne avaient perdu le gras retrouve un contenu d'un seul tenant.**
  Un saut de ligne n'a pas de dessin, gras ou non n'y change rien — rien ne se voyait — mais il
  perd sa mise en forme au moindre remaniement du texte autour de lui, et chacun de ces accidents
  coupait la note en deux morceaux de plus. Une note s'était ainsi retrouvée en 1 759 tronçons pour
  108 Ko là où un seul de 39 Ko suffisait. Elle se remet d'aplomb à sa prochaine ouverture.
- **La barre de mise en forme suit enfin le curseur.** Sélectionner un passage en gras allume le
  bouton B, et une seule pression suffit désormais pour l'enlever — il en fallait deux, la première
  ajoutant du gras là où il y en avait déjà. Même chose pour l'italique et les couleurs.
- En paysage, le clavier passait en plein écran et recopiait le texte entier vers son propre
  processus à chaque frappe.

### Notes techniques
- La cause tient à deux mécanismes du cadre applicatif qui se combinent mal : insérer un caractère
  décale la position de tous les spans situés après le curseur et `SpannableStringBuilder` annonce
  un changement pour chacun ; or `StyleSpan` et `ForegroundColorSpan` descendent de
  `MetricAffectingSpan`, qui implémente `UpdateLayout`, si bien que `DynamicLayout` remet en page
  toute l'étendue de chaque span concerné, deux fois. La quantité de texte recalculée est donc la
  même quel que soit le découpage : un seul span coûtait 105 ms par frappe, dix-neuf en coûtaient
  322, mille quatre cent quarante-quatre en coûtaient 4 648. Découper la mise en forme a été
  essayé, mesuré, puis retiré.
- Un passage fait de seuls sauts de ligne ne départage plus les tronçons : il suit ce qui l'entoure.
  La règle vaut à l'écriture comme à la lecture, de sorte qu'une note déjà émiettée en base se
  répare à l'ouverture sans attendre un enregistrement. La relecture refusionne également les
  tronçons voisins de même mise en forme au lieu de faire confiance à ce qu'elle reçoit.
- La recherche des frontières de mise en forme ne porte plus que sur les deux types réellement
  enregistrés : elle trébuchait aussi sur les spans du correcteur orthographique, qui en sème un
  par mot douteux.
- L'état de la barre est relu sous le curseur à chaque déplacement de la sélection. Comme cela
  s'exécute aussi souvent que la saisie — et qu'effacer en maintenant la touche va bien plus vite
  que taper — la relecture ne touche à aucune vue tant que l'état n'a pas changé.

## [2.6.0] — 2026-08-15

### Ajouté
- **Un véritable agenda remplace l'écran Calendrier.** La même donnée s'y lit sous trois
  découpages — jour, semaine, mois — auxquels s'ajoute un planning continu qui répond à
  « qu'est-ce qui vient ensuite » sans imposer de choisir une période : les journées vides en sont
  simplement absentes. Le découpage choisi se retient d'une session à l'autre.
- **Un événement a maintenant un horaire, une durée et une place.** Début et fin, journée entière,
  description, lieu et couleur de palette. Les événements qui se chevauchent se répartissent en
  colonnes côte à côte dans les vues jour et semaine, comme dans n'importe quel agenda.
- **Répétitions.** Tous les jours, toutes les semaines, tous les mois ou tous les ans, avec un
  intervalle, un choix de jours pour les répétitions hebdomadaires, et deux motifs mensuels — « le
  13 » ou « le 2ᵉ jeudi », qui tombent sur la même date le mois où on les choisit et divergent
  ensuite. La série se termine à une date, après un nombre d'occurrences, ou jamais. Un résumé en
  une ligne — « Toutes les 2 semaines, lundi, mercredi » — se lit sans ouvrir le sélecteur.
- **Rappels**, de l'heure dite jusqu'à deux jours avant, plusieurs par événement.
- **Modifier une occurrence d'une série demande sa portée** : cette occurrence, les suivantes, ou
  toute la série. La question n'est posée que lorsqu'elle se pose — un événement unique n'a qu'une
  portée possible.
- **Recherche dans l'agenda.** Passé les quelques semaines qu'on parcourt à la main, retrouver un
  rendez-vous demandait de savoir déjà quand il avait eu lieu. Les résultats sont des occurrences
  et non des lignes : une série trouvée rend ses dates.

### Modifié
- La tuile d'accueil compte les occurrences du jour et non les lignes en base : une série n'occupe
  qu'une ligne mais peut avoir un rendez-vous aujourd'hui.

### Notes techniques
- **Le stockage passe des dates texte aux instants.** Une clé `yyyy-MM-dd` ne sait rien dire d'un
  événement à cheval sur deux jours, ni d'une série dont la seule ligne porte la date de sa
  *première* occurrence. Le prédicat de lecture est donc partout le chevauchement
  (`startUtc < finDePlage AND seriesEndUtc > débutDePlage`), jamais un `BETWEEN` sur le début.
  `seriesEndUtc` est dérivé de la règle, donc redondant, mais c'est lui qui permet à SQLite
  d'écarter une série hors plage sans avoir à la déplier.
- **Deux ancrages cohabitent, délibérément** : un événement à l'heure porte un instant absolu, une
  journée entière porte minuit UTC de sa date et n'est jamais convertie — « le 14 août » doit
  rester le 14 août où que l'on ouvre l'application. C'est la convention de `CalendarContract` et
  d'iCalendar.
- **Le dépliage d'une série calcule en heure murale locale**, la conversion en instant n'ayant lieu
  qu'à la toute fin : « tous les jours à 9 h » ajoute un jour civil et non 86 400 000
  millisecondes, faute de quoi le rendez-vous glisserait à 8 h la nuit du changement d'heure.
  Le fuseau est toujours passé en paramètre, jamais lu depuis la machine : une expansion est donc
  reproductible et vérifiable hors d'Android.
- **Une seule alarme est vivante à la fois**, réarmée après chaque déclenchement. Poser une alarme
  par rappel serait impossible : une série sans terme a une infinité d'occurrences, et Android
  plafonne les alarmes exactes à 500 par application depuis l'API 34. Le prochain rappel est
  cherché dans une fenêtre glissante de sept jours, avec une alarme de garde quand elle est vide.
  Le nombre d'alarmes devient ainsi indépendant des données.
- Les grilles peignent leurs lignes d'heures et de jours dans `onDraw` plutôt que de les composer
  en vues : vingt-quatre lignes sur sept colonnes feraient cent soixante-huit vues à mesurer et
  poser à chaque défilement, pour des traits d'un pixel. Les blocs restent de vraies vues, donc
  tapotables et lisibles par un lecteur d'écran.
- La migration reprend les événements existants sans finesse, c'est assumé : aucun ne portait de
  durée, donc il n'y a rien à reconstituer. Chacun devient un événement simple d'une heure, borné
  au jour même.
- Le schéma Room est désormais exporté et versionné (`app/schemas/`) : c'est la référence qui
  permet d'écrire les migrations suivantes et de voir en revue ce qu'une évolution change.
- Soixante et un tests unitaires couvrent le cœur, sans appareil : expansion des récurrences,
  analyse et rendu des RRULE, scission d'une série, répartition des chevauchements, bornes
  d'occurrence.

## [2.5.1] — 2026-08-13

### Corrigé
- **L'édition d'une note volumineuse ne traîne plus.** À chaque lettre tapée ou effacée, Android
  prévenait le remplissage automatique et la capture de contenu en leur passant la note entière,
  copiée puis expédiée au système. Sur une note de plusieurs dizaines de milliers de caractères,
  cela se répétait entre chaque frappe. Les champs de l'éditeur ne s'annoncent plus à ces deux
  services, qui n'ont rien à faire dans une note.
- **L'enregistrement automatique ne se met plus en travers de la saisie.** La conversion du corps
  se faisait sur le fil principal, 700 ms après la dernière frappe — c'est-à-dire pile au moment
  où l'on marque une pause. Seule la lecture des champs y reste désormais.
- **Une note plus longue que la tranche lue pour les aperçus retrouve le sien.** Le corps d'une
  note de texte ne formant qu'un seul élément, la carte de la grille restait muette au-delà de
  4 000 caractères.
- Le déplacement d'une ligne de liste interrompu par une sortie d'écran — un appel entrant en
  plein glisser — n'est plus perdu.
- Ce qui est tapé pendant la lecture d'une note volumineuse n'est plus balayé par le remplissage
  des champs : ils n'acceptent la frappe qu'une fois le corps arrivé.

### Notes techniques
- L'éditeur suit un compteur de modifications : savoir s'il y a quelque chose à écrire ne coûte
  plus une resérialisation complète de la note. Sortir de l'écran juste après un enregistrement
  automatique ne réécrit donc plus rien, et ouvrir une note ne regénère plus son AST pour rien.
- Une écriture rendue périmée par la frappe suivante est annulée plutôt que menée à son terme :
  deux copies d'un corps volumineux ne vivent plus en parallèle. La section qui touche la base
  est en revanche ininterruptible — une insertion coupée en deux réinsérerait la même clé
  primaire. Un verrou et le rang de la version écrite empêchent une version périmée d'en écraser
  une plus récente.
- Le minuteur s'espace à 3 s au-delà de 20 000 caractères, mais rien ne reste en mémoire seule
  plus de 5 s : un minuteur qui repart à chaque touche ne promet rien à qui écrit sans jamais
  s'arrêter, et celui de 700 ms ne le promettait pas davantage.
- Les tronçons voisins de même mise en forme sont fusionnés à l'écriture : moins de spans à
  replacer à chaque frappe une fois la note rouverte, et un AST plus court.
- `TruncatedJson` referme un texte coupé en plein milieu, échappements `\n` et `\uXXXX` compris.
  Sept tests unitaires le couvrent, exécutables sans appareil : `org.json` n'étant qu'une coquille
  vide dans le android.jar des tests unitaires, la vraie implémentation leur est fournie.

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

[2.5.1]: https://github.com/majdus/Organisateur/releases/tag/v2.5.1
[2.5.0]: https://github.com/majdus/Organisateur/releases/tag/v2.5.0
[2.4.0]: https://github.com/majdus/Organisateur/releases/tag/v2.4.0
[2.3.0]: https://github.com/majdus/Organisateur/releases/tag/v2.3.0
[2.2.0]: https://github.com/majdus/Organisateur/releases/tag/v2.2.0
[2.1.0]: https://github.com/majdus/Organisateur/releases/tag/v2.1.0
[2.0.0]: https://github.com/majdus/Organisateur/releases/tag/v2.0.0
