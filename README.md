# kotlin-counterparty-risk

[🇬🇧 English version](README.en.md)

[![Build](https://github.com/riadh-mnasri/kotlin-counterparty-risk/actions/workflows/ci.yml/badge.svg)](https://github.com/riadh-mnasri/kotlin-counterparty-risk/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **⚠️ Outil pédagogique et de prototypage, pas un moteur réglementaire.** Les formules, seuils et tables (PD, LGD, décotes) sont volontairement simplifiés, la table de PD s'inspirant de moyennes publiques d'agence de notation à titre indicatif et le reste étant **défini par l'application** — dans tous les cas, rien de ceci n'est issu d'un texte réglementaire ni utilisable comme tel. Ne servez-vous jamais de cette librairie pour un vrai calcul de fonds propres réglementaires ou une décision de risque réelle. Voir la section [Ce que la librairie ne fait pas](#ce-que-la-librairie-ne-fait-pas-encore) pour le détail des simplifications.

Une librairie Kotlin pour calculer l'exposition au risque de contrepartie (EAD), la perte attendue et une CVA simplifiée sur des opérations de financement sur titres (repos, prêts de titres), avec contrôle de limite de crédit.

## Pourquoi cette librairie

`finmath-lib` (mathématiques financières générales) et `Strata` d'OpenGamma (analytics de risque de marché) sont des librairies Java matures, mais aucune des deux ne propose de calcul d'exposition de risque de contrepartie prêt à l'emploi. Un concurrent existe en Python (`creditriskengine`) mais rien d'équivalent en Kotlin/JVM. `kotlin-counterparty-risk` comble ce vide, avec un objectif pédagogique assumé plutôt que la couverture réglementaire complète de ces librairies.

## Ce que fait la librairie

- **Calcul d'exposition (EAD)** via une version simplifiée de l'« approche globale avec décotes prudentielles » de Bâle pour les SFT (repos, prêts de titres).
- **Perte attendue (Expected Loss)** : `EL = PD × LGD × EAD`.
- **CVA simplifiée** : approximation linéaire mono-période à partir de l'EAD, de la PD, de la LGD et de la maturité restante, ou **CVA multi-période** en découpant l'horizon en périodes via une courbe de PD à taux de hasard constant, avec actualisation optionnelle.
- **Contrôle de limite de crédit** : statut OK / WARNING (80% de la limite) / BREACH.
- **Profil d'exposition simulé (PFE/EPE)** : simulation Monte Carlo (mouvement brownien géométrique sans dérive) de l'exposition dans le temps, EPE et PFE par pas de temps, EPE effective et exposition IMM (`alpha x EPE effective`).
- Arithmétique en `BigDecimal` pour tous les montants et taux (`Money`, `Rate`), choix délibéré pour une librairie financière ; `Double` n'apparaît qu'en interne pour les calculs transcendants sans équivalent `BigDecimal` natif (puissance à exposant fractionnaire dans `CreditCurve`, tirages aléatoires gaussiens dans la simulation Monte Carlo), toujours reconverti en `BigDecimal` immédiatement.

## Ce que la librairie ne fait pas (encore)

- La table de probabilité de défaut s'inspire des moyennes long terme publiées par S&P Global Ratings (études annuelles « Default, Transition, and Recovery »), à titre indicatif — pas les chiffres exacts d'une étude ni un usage réglementaire. La table de décotes reste, elle, **définie par l'application**, pas issue d'un texte réglementaire (Bâle, CRR...).
- La CVA multi-période (`computeMultiPeriodCva`) existe, avec une courbe de crédit simplifiée (`CreditCurve`, taux de hasard constant extrapolé depuis la seule PD à 1 an) — pas une vraie courbe multi-échéances de marché ou d'agence.
- L'exposition simulée dans le temps (`simulateExposureProfile`) existe (PFE/EPE par Monte Carlo, EPE effective, exposition IMM avec multiplicateur alpha), mais reste un seul facteur de risque brownien sans dérive, sans remargining du collatéral, sans changement de composition du netting set dans le temps, et sans corrélation multi-facteurs — pas un vrai moteur IMM.
- La perte en cas de défaut (LGD) est un taux fixe de 45% par défaut ; `AssetClass` porte désormais un taux LGD indicatif par classe d'actif, utilisable en le passant explicitement à `computeExpectedLoss`, mais rien ne relie automatiquement l'EAD à un type de collatéral précis.
- La décote FX est un taux fixe de 8% par défaut (`FxHaircutTable.FLAT`) ; une table `FxHaircutTable` par paire de devises existe désormais et peut être passée à `computeExposure`, mais reste, elle aussi, définie par l'application.

Ce sont de bons points de départ pour une première contribution, voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Installation

La librairie n'est pas encore publiée sur Maven Central. En attendant :

```bash
git clone https://github.com/riadh-mnasri/kotlin-counterparty-risk.git
cd kotlin-counterparty-risk
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.riadh-mnasri:kotlin-counterparty-risk:0.1.0")
}
```

## Comment utiliser la librairie

Comme pour [kotlin-chess-tournament](https://github.com/riadh-mnasri/kotlin-chess-tournament), toutes les fonctions sont pures : elles prennent des données en entrée et renvoient un résultat, sans état caché.

### 1. Créer une contrepartie

```kotlin
import io.github.riadhmnasri.counterpartyrisk.entity.Counterparty
import io.github.riadhmnasri.counterpartyrisk.model.CreditRating
import io.github.riadhmnasri.counterpartyrisk.model.Currency
import io.github.riadhmnasri.counterpartyrisk.model.Money
import java.math.BigDecimal

val usd = Currency("USD")

val counterparty = Counterparty(
    id = "cp-1",
    name = "Acme Capital Partners",
    rating = CreditRating.BBB,
    approvedLimit = Money(BigDecimal("250000"), usd),
)
```

### 2. Décrire les transactions et leur collatéral

```kotlin
import io.github.riadhmnasri.counterpartyrisk.entity.CollateralPosition
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransaction
import io.github.riadhmnasri.counterpartyrisk.entity.SftTransactionKind
import io.github.riadhmnasri.counterpartyrisk.model.AssetClass

val transaction = SftTransaction(
    id = "sft-1",
    kind = SftTransactionKind.SECURITIES_LENDING,
    exposureAmount = Money(BigDecimal("1000000"), usd),
    exposureCurrency = usd,
    exposureAssetClass = AssetClass.EQUITY,
    remainingTenorDays = 30,
    collateral = listOf(
        CollateralPosition(Money(BigDecimal("950000"), usd), AssetClass.CASH, usd),
    ),
)
```

### 3. Regrouper les transactions dans une netting set

```kotlin
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet

val nettingSet = NettingSet(
    id = "ns-1",
    counterpartyId = counterparty.id,
    reportingCurrency = usd,
    transactions = listOf(transaction),
)
```

### 4. Calculer l'exposition (EAD)

```kotlin
import io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure

val exposure = computeExposure(nettingSet)
// exposure.eStar : exposition nette après décotes
// exposure.ead   : Exposure at Default (= eStar pour des SFT standardisées)
```

### 5. Calculer la perte attendue et la CVA

```kotlin
import io.github.riadhmnasri.counterpartyrisk.creditrisk.computeExpectedLoss
import io.github.riadhmnasri.counterpartyrisk.cva.computeCva

val expectedLoss = computeExpectedLoss(counterparty, exposure.ead)
val cva = computeCva(counterparty, nettingSet, exposure.ead)
```

### 6. Vérifier la limite de crédit

```kotlin
import io.github.riadhmnasri.counterpartyrisk.risklimit.checkRiskLimit

val status = checkRiskLimit(counterparty, exposure.ead)
// LimitStatus.OK, WARNING (au-delà de 80% de la limite) ou BREACH
```

### Exemple complet

Pour voir ces six étapes enchaînées sur une netting set à deux transactions, avec un rapport imprimé, voir [`examples/src/main/kotlin/.../RunSampleRiskReport.kt`](examples/src/main/kotlin/io/github/riadhmnasri/counterpartyrisk/examples/RunSampleRiskReport.kt), exécutable avec :

```bash
./gradlew :examples:run
```

## Glossaire

- **EAD (Exposure at Default)** : le montant qu'une banque risque de perdre si la contrepartie fait défaut, après prise en compte du collatéral.
- **E\* (Net Exposure)** : l'exposition nette après l'approche globale avec décotes prudentielles ; ici `EAD = E*`.
- **Haircut (décote)** : une réduction prudente appliquée à la valeur d'un actif ou d'un collatéral, pour tenir compte du risque qu'il perde de la valeur avant de pouvoir être liquidé.
- **PD (Probability of Default)** : la probabilité qu'une contrepartie fasse défaut sur un horizon donné (ici, 1 an).
- **LGD (Loss Given Default)** : la part de l'exposition qui serait réellement perdue en cas de défaut (le complément est recouvré).
- **Expected Loss (perte attendue)** : `PD × LGD × EAD`, la perte moyenne anticipée sur l'horizon considéré.
- **CVA (Credit Valuation Adjustment)** : l'ajustement de valeur qui reflète le risque de contrepartie, c'est-à-dire combien coûte le risque que la contrepartie fasse défaut avant l'échéance.
- **Netting Set** : un ensemble de transactions avec une même contrepartie, compensées entre elles sous un accord-cadre (GMRA pour les repos, GMSLA pour les prêts de titres), pour calculer une exposition nette plutôt que brute transaction par transaction.
- **Repo / Securities Lending (SFT)** : des opérations de financement sur titres, où un repo échange du cash contre des titres remis en garantie et un prêt de titres prête un titre contre du collatéral (souvent du cash).
- **Credit Limit (limite de crédit)** : le montant maximal d'exposition qu'une institution accepte de prendre sur une contrepartie donnée.

## Qualité et développement

```bash
./gradlew build          # compile, teste, vérifie le style et l'analyse statique
./gradlew test            # tests uniquement
./gradlew ktlintFormat     # reformate automatiquement le code
./gradlew dokkaHtml       # génère la documentation API
```

Le projet utilise [ktlint](https://github.com/pinterest/ktlint), [detekt](https://detekt.dev/), [Kover](https://github.com/Kotlin/kotlinx-kover) et [Dokka](https://github.com/Kotlin/dokka). Tout le code métier a été écrit en TDD (tests d'abord).

## Publication

Le projet est déjà configuré pour publier sur Maven Central (plugin, POM, signature), il ne manque que la configuration ponctuelle des comptes et clés. Voir [PUBLISHING.md](PUBLISHING.md).

## Contribuer

Les contributions sont bienvenues, voir [CONTRIBUTING.md](CONTRIBUTING.md) et [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). La section « Ce que la librairie ne fait pas (encore) » ci-dessus est un bon point de départ pour une première contribution.

## Licence

[MIT](LICENSE) © 2026 Riadh MNASRI
