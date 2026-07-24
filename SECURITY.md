# Security Policy / Politique de sécurité

## English

This library computes risk figures (exposure, expected loss, CVA) from data you provide; it does not handle network input, authentication, or untrusted file parsing, so its attack surface is small. It is explicitly **not** a regulatory or production risk engine (see the disclaimer at the top of the README), so a bug here should not be treated as a financial security incident, but if you find a genuine security issue (for example, an input that causes a crash or an infinite loop), please report it privately by emailing **riadh.mnasri@gmail.com** rather than opening a public issue.

Please include:

- A description of the issue and its potential impact
- Steps to reproduce it, ideally with a minimal code sample

You should expect an initial response within a few days. Once a fix is available, it will be released and the reporter credited, unless anonymity is requested.

## Français

Cette librairie calcule des indicateurs de risque (exposition, perte attendue, CVA) à partir de données que vous fournissez ; elle ne traite ni entrée réseau, ni authentification, ni fichiers non fiables, donc sa surface d'attaque est réduite. Ce n'est explicitement **pas** un moteur de risque réglementaire ou de production (voir le disclaimer en tête du README), donc un bug ici ne doit pas être traité comme un incident de sécurité financière, mais si vous trouvez malgré tout un vrai problème de sécurité (par exemple une entrée qui provoque un plantage ou une boucle infinie), merci de le signaler en privé par email à **riadh.mnasri@gmail.com** plutôt que d'ouvrir une issue publique.

Merci d'inclure :

- Une description du problème et de son impact potentiel
- Les étapes pour le reproduire, idéalement avec un exemple de code minimal

Vous pouvez attendre une première réponse sous quelques jours. Une fois un correctif disponible, il sera publié et la personne ayant signalé le problème sera créditée, sauf demande d'anonymat.
