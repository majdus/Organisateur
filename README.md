# Organisateur

Application Android d'organisation personnelle : tâches, notes, rappels et calendrier, réunis
sous un rendu clair unique.

## Écrans

- **Tâches** — liste réorganisable au doigt, les terminées groupées en bas.
- **Notes** — grille de notes en texte enrichi ou en liste à cocher, une couleur par note.
- **Rappels** — réveils quotidiens, chacun activable indépendamment.
- **Calendrier** — grille mensuelle avec pastilles d'événements et agenda du jour sélectionné.

## Réglages

Les réglages sont propres à l'écran qui les porte et s'ouvrent par la roue dentée de sa barre
d'outils. Chacun est retenu d'une ouverture à l'autre.

### Tâches

| Réglage | Options | Défaut |
| --- | --- | --- |
| Nouvelle tâche | En haut / En bas de la liste | En haut |
| Tâche cochée | Barrée / Supprimée | Barrée |

« Barrée » envoie la tâche cochée rejoindre les terminées, en bas de la liste. « Supprimée » la
retire, avec un Snackbar « Annuler » qui la remet **décochée** — revenir sur un cochage, c'est
retrouver une tâche à faire. Le commutateur « Tâche terminée » de la feuille d'édition suit le
même réglage que la case de la carte.

Aucun des deux réglages ne touche aux tâches déjà en base : ils ne décident que du sort de la
prochaine tâche créée et de la prochaine case cochée.

### Calendrier

| Réglage | Options | Défaut |
| --- | --- | --- |
| Système calendaire | Grégorien / Hijri (Umm al-Qura) | Grégorien |

Le choix ne touche que l'affichage. En mode hijri, la correspondance grégorienne reste lisible à
trois endroits : les mois couverts sous le titre du mois, un « 5/08 » dans chaque case, et la
date civile sous l'en-tête du jour sélectionné.

## Compilation

```sh
./gradlew assembleDebug
```

La variante release se signe à partir d'un `keystore.properties` non versionné, sur le modèle de
`keystore.properties.example`. En son absence la compilation aboutit quand même, sur un APK non
signé.

Le journal des versions est dans [CHANGELOG.md](CHANGELOG.md).
