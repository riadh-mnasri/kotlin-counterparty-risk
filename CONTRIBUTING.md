# Contribuer

[🇬🇧 English version](CONTRIBUTING.en.md)

Merci de l'intérêt porté à ce projet ! Les contributions sont les bienvenues, qu'il s'agisse d'un rapport de bug, d'une amélioration de la documentation ou d'une nouvelle fonctionnalité.

## Par où commencer

La section « Ce que la librairie ne fait pas (encore) » du [README](README.md) liste des limitations connues et assumées : ce sont de bons points de départ pour une première contribution. Les issues étiquetées [`good first issue`](https://github.com/riadh-mnasri/kotlin-counterparty-risk/labels/good%20first%20issue) sont pensées pour être accessibles sans connaître tout le projet.

## Mettre en place le projet en local

```bash
git clone https://github.com/riadh-mnasri/kotlin-counterparty-risk.git
cd kotlin-counterparty-risk
./gradlew build
```

Ceci compile le projet, exécute les tests, et vérifie le style de code (ktlint) et l'analyse statique (detekt).

## Style de code et TDD

- Le code métier est écrit en TDD : le test s'écrit avant l'implémentation.
- Les tests suivent la structure `// Given` / `// When` / `// Then` en commentaire.
- Le formatage est géré par ktlint : lancez `./gradlew ktlintFormat` avant de committer plutôt que de formater à la main.
- `./gradlew detekt` doit passer sans nouvel avertissement.
- L'arithmétique reste en `BigDecimal` de bout en bout, jamais en `Double`, pour rester cohérent avec le reste du code financier.
- Privilégiez des noms explicites et des fonctions courtes à des commentaires expliquant un code compliqué.

## Sur les données financières (PD, LGD, décotes...)

Les tables de probabilité de défaut, de décotes et le taux de LGD sont **volontairement simplifiées et définies par l'application**, pas des vraies valeurs réglementaires ou d'agence de notation (voir le disclaimer en tête du README). Si vous proposez d'affiner une de ces tables (par exemple une vraie table de PD par agence), merci d'ouvrir une issue d'abord pour discuter de la source et de la portée, plutôt que d'arriver directement avec une pull request : ce projet reste un outil pédagogique, pas un moteur réglementaire, et toute donnée ajoutée doit rester honnêtement documentée comme telle.

## Proposer une modification

1. Ouvrez une issue avant un gros changement, pour discuter de l'approche.
2. Créez une branche depuis `main`.
3. Committez avec des messages clairs décrivant le *pourquoi* du changement.
4. Vérifiez que `./gradlew build` passe entièrement avant d'ouvrir la pull request.
5. Décrivez dans la pull request ce qui change et pourquoi, en citant l'issue liée le cas échéant.

## Signaler un bug ou proposer une fonctionnalité

Utilisez les templates d'issue GitHub fournis. Plus le rapport est précis (cas d'entrée, comportement attendu, comportement observé), plus vite il pourra être traité.

## Code de conduite

Ce projet suit le [Contributor Covenant](CODE_OF_CONDUCT.md). En participant, vous acceptez de le respecter.
