# kotlin-counterparty-risk

[🇫🇷 Version française](README.md)

[![Build](https://github.com/riadh-mnasri/kotlin-counterparty-risk/actions/workflows/ci.yml/badge.svg)](https://github.com/riadh-mnasri/kotlin-counterparty-risk/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **⚠️ Teaching and prototyping tool, not a regulatory engine.** The formulas, thresholds and tables (PD, LGD, haircuts) are deliberately simplified and **app-defined**, not sourced from a rating agency or a regulatory text. Never use this library for a real regulatory capital calculation or an actual risk decision. See [What the library does not do](#what-the-library-does-not-do-yet) for the full list of simplifications.

A Kotlin library for computing counterparty credit risk exposure (EAD), expected loss and a simplified CVA on securities financing transactions (repos, securities lending), with credit limit checking.

## Why this library

`finmath-lib` (general mathematical finance) and OpenGamma's `Strata` (market risk analytics) are mature Java libraries, but neither offers a ready-to-use counterparty credit risk exposure calculation. A competitor exists in Python (`creditriskengine`) but nothing equivalent on the Kotlin/JVM side. `kotlin-counterparty-risk` fills that gap, with an explicit teaching focus rather than the full regulatory coverage of those libraries.

## What the library does

- **Exposure calculation (EAD)** via a simplified version of the Basel "comprehensive approach with supervisory haircuts" for SFTs (repos, securities lending).
- **Expected Loss**: `EL = PD × LGD × EAD`.
- **Simplified CVA**: a single-period linear approximation from EAD, PD, LGD and the remaining maturity.
- **Credit limit checking**: OK / WARNING (80% of the limit) / BREACH status.
- `BigDecimal` arithmetic throughout (no `Double`), a deliberate choice for a finance library.

## What the library does not do (yet)

- The probability-of-default and haircut tables are **app-defined**, not real rating agency or regulatory (Basel, CRR...) tables.
- No multi-period CVA or credit curve: this is a single-point-in-time linear approximation.
- No simulated exposure over time (Monte Carlo PFE/EPE), no IMM approach with an alpha multiplier — this is a point-in-time exposure calculation.
- Loss Given Default (LGD) is a flat 45% rate, not modeled per collateral type.

These are good starting points for a first contribution, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Installation

The library is not published on Maven Central yet. In the meantime:

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
    implementation("io.github.riadh-mnasri:kotlin-counterparty-risk:0.1.0-SNAPSHOT")
}
```

## How to use the library

Like [kotlin-chess-tournament](https://github.com/riadh-mnasri/kotlin-chess-tournament), every function is pure: it takes data in and returns a result, with no hidden state.

### 1. Create a counterparty

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

### 2. Describe the transactions and their collateral

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

### 3. Group the transactions into a netting set

```kotlin
import io.github.riadhmnasri.counterpartyrisk.entity.NettingSet

val nettingSet = NettingSet(
    id = "ns-1",
    counterpartyId = counterparty.id,
    reportingCurrency = usd,
    transactions = listOf(transaction),
)
```

### 4. Compute the exposure (EAD)

```kotlin
import io.github.riadhmnasri.counterpartyrisk.exposure.computeExposure

val exposure = computeExposure(nettingSet)
// exposure.eStar : net exposure after haircuts
// exposure.ead   : Exposure at Default (= eStar for standardized SFTs)
```

### 5. Compute expected loss and CVA

```kotlin
import io.github.riadhmnasri.counterpartyrisk.creditrisk.computeExpectedLoss
import io.github.riadhmnasri.counterpartyrisk.cva.computeCva

val expectedLoss = computeExpectedLoss(counterparty, exposure.ead)
val cva = computeCva(counterparty, nettingSet, exposure.ead)
```

### 6. Check the credit limit

```kotlin
import io.github.riadhmnasri.counterpartyrisk.risklimit.checkRiskLimit

val status = checkRiskLimit(counterparty, exposure.ead)
// LimitStatus.OK, WARNING (above 80% of the limit), or BREACH
```

### Full example

To see these six steps chained together over a netting set with two transactions, printing a full report, see [`examples/src/main/kotlin/.../RunSampleRiskReport.kt`](examples/src/main/kotlin/io/github/riadhmnasri/counterpartyrisk/examples/RunSampleRiskReport.kt), runnable with:

```bash
./gradlew :examples:run
```

## Glossary

- **EAD (Exposure at Default)**: the amount a bank stands to lose if the counterparty defaults, after accounting for collateral.
- **E\* (Net Exposure)**: the net exposure after the comprehensive haircut approach; here `EAD = E*`.
- **Haircut**: a prudent reduction applied to the value of an asset or collateral, to account for the risk that it loses value before it can be liquidated.
- **PD (Probability of Default)**: the probability that a counterparty defaults over a given horizon (here, 1 year).
- **LGD (Loss Given Default)**: the share of the exposure that would actually be lost in a default (the rest is recovered).
- **Expected Loss**: `PD × LGD × EAD`, the average anticipated loss over the horizon considered.
- **CVA (Credit Valuation Adjustment)**: the value adjustment reflecting counterparty risk, i.e. how much the risk of the counterparty defaulting before maturity is worth.
- **Netting Set**: a group of transactions with the same counterparty, netted together under a master agreement (GMRA for repos, GMSLA for securities lending), to compute a net exposure rather than a gross, transaction-by-transaction one.
- **Repo / Securities Lending (SFT)**: securities financing transactions — a repo exchanges cash for securities posted as collateral, securities lending lends out a security against collateral (often cash).
- **Credit Limit**: the maximum exposure amount an institution is willing to take on a given counterparty.

## Quality and development

```bash
./gradlew build          # compiles, tests, checks style and static analysis
./gradlew test            # tests only
./gradlew ktlintFormat     # auto-formats the code
./gradlew dokkaHtml       # generates the API documentation
```

The project uses [ktlint](https://github.com/pinterest/ktlint), [detekt](https://detekt.dev/), [Kover](https://github.com/Kotlin/kotlinx-kover), and [Dokka](https://github.com/Kotlin/dokka). All business logic was written test-first (TDD).

## Publishing

The project is already configured to publish to Maven Central (plugin, POM, signing); only the one-time account and key setup is missing. See [PUBLISHING.md](PUBLISHING.md).

## Contributing

Contributions are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). The "What the library does not do (yet)" section above is a good starting point for a first contribution.

## License

[MIT](LICENSE) © 2026 Riadh MNASRI
