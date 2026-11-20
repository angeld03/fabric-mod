---
description: Revisa todos los PRs abiertos y dice cuáles se pueden fusionar sin pisarse
agent: revisor
---

Revisa todos los Pull Requests abiertos de `angeld03/fabric-mod` usando el subagente revisor.

Dime para cada PR:

- Qué archivos toca.
- Si se solapa con otro PR abierto (riesgo de pisón).
- Si su rama está al día con `main`.
- Si compila (ejecuta `.\gradlew build` si puedes).
- Veredicto: SEGURO / REVISAR / PELIGROSO.

Al final, dime cuáles se pueden fusionar y cuáles hay que arreglar primero. No fusiones ni modifiques nada.
