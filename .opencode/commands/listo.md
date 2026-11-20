---
description: Prepara el cambio actual para entregarlo: comprueba, commitea y sube la rama
---

Antes de nada, comprueba en qué rama estamos. Si es `main`, crea una rama nueva con el prefijo del usuario y para aquí avisando.

Luego:

1. Muestra `git status` y `git diff --stat`.
2. Ejecuta `.\gradlew build` (o `./gradlew build`) y dime si compila.
3. Si compila, haz commit con un mensaje claro y sube la rama con `git push -u origin <rama>`.
4. Dime el enlace para crear el Pull Request: `https://github.com/angeld03/fabric-mod/compare/main...<rama>?expand=1`.

Nunca hagas push a `main`, nunca uses `--force`, y nunca hagas `commit --amend` si ya se subió la rama.
