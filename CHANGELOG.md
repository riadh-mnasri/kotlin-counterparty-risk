# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Each `AssetClass` now carries an app-defined `lgd` (Loss Given Default) assumption. `computeExpectedLoss` gains an optional `lgd` parameter so callers can vary LGD by collateral quality instead of the flat 45% default.

## [0.1.0] - 2026-09-07

### Added

- Money, Rate and Currency value objects, `BigDecimal`-based throughout.
- AssetClass and CreditRating reference tables with app-defined haircuts and probabilities of default, plus a flat FX haircut.
- CollateralPosition, SftTransaction, NettingSet and Counterparty entities with validation.
- Exposure calculation (EAD) via a simplified comprehensive haircut approach.
- Expected Loss and a simplified single-period CVA calculation.
- Credit limit checking (OK / WARNING / BREACH).
- A runnable sample risk report under `examples/`.
- Bilingual README (French/English) with a strong up-front disclaimer and a glossary, CONTRIBUTING guide, Code of Conduct, and security policy.
