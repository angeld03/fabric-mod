# Cómo colaborar sin pisarnos

La regla es simple: **main es sagrada**. Nadie escribe en ella directamente. Todo pasa por una rama propia + Pull Request.

## 1. Preparación inicial (solo una vez)

```bash
git clone https://github.com/angeld03/fabric-mod.git
cd fabric-mod
git config core.hooksPath .githooks
```

Ese último comando activa la **revisión automática**: antes de cada `push`, OpenCode ejecuta el subagente revisor. Si detecta un PR peligroso, el push se bloquea.

## 2. Revisión automática

En OpenCode, `/revisar` lanza el subagente revisor. También se ejecuta solo en cada push gracias al hook.

El revisor **solo informa**: no puede editar, commitear, hacer push, merge ni revert. Da un veredicto por PR: **SEGURO**, **REVISAR** o **PELIGROSO**.

Para terminar una tarea, `/listo` comprueba la rama, compila, commitea y sube.

## 3. Trabajar

Crea una rama por tarea, con tu nombre como prefijo para que nunca choquen dos ramas:

```bash
git switch main
git pull --ff-only
git switch -c tu-nombre/breve-descripcion
# ... editas archivos ...
git add .
git commit -m "Añade tal cosa"
git push -u origin tu-nombre/breve-descripcion
```

Después abres el Pull Request en GitHub (la URL te la da el propio `git push`).

## 4. Normas anti-conflictos

- **Un archivo, un responsable.** Si vas a tocar un archivo que no es tuyo, avisa en el PR antes.
- **Nada de `git push --force`** (bloqueado en `main` y desaconsejado en tus ramas).
- **Nada de `git commit --amend`** después de haber hecho `push`.
- **Sincroniza a menudo:** `git fetch origin` + `git rebase origin/main` en tu rama antes del PR.
- **No edites `gradle.properties`, `build.gradle` ni `fabric.mod.json` a la vez.** Los cambios de versiones van en un PR aparte y los revisan ambos.
- **Cada funcionalidad, en su propio archivo Java.** Así dos personas casi nunca tocan el mismo fichero.
- `.gitattributes` fuerza finales de línea LF para evitar conflictos entre Windows y Linux.

## 5. Fusionar

- Se usa **Squash and merge**: el historial de `main` queda lineal y limpio.
- El PR necesita **1 aprobación** de la otra persona.
- Se borra la rama después de fusionar.
- **Nunca se deshace una fusión automáticamente.** Si algo salió mal, se hace `git revert` a mano y eso crea un nuevo PR que también se revisa.

## 6. Si aparece un conflicto

No lo fuerces. En tu rama:

```bash
git fetch origin
git rebase origin/main
# edita los archivos marcados, luego:
git add .
git rebase --continue
git push --force-with-lease   # solo en TU rama, nunca en main
```

Y coméntalo en el PR para que el otro lo vea.
