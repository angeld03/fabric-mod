# Cómo colaborar sin pisarnos

La regla es simple: **main es sagrada**. Nadie escribe en ella directamente. Todo pasa por una rama propia + Pull Request.

## 1. Preparación inicial (solo una vez)

```bash
git clone https://github.com/angeld03/fabric-mod.git
cd fabric-mod
git switch -c tu-nombre/setup
```

## 2. Trabajar

Crea una rama por tarea, con tu nombre como prefijo para que nunca choquen dos ramas:

```bash
git switch main
git pull --ff-only
git switch -c tu-nombre/breve-descripcion
# ... editasARCHIVOS ...
git add .
git commit -m "Añade tal cosa"
git push -u origin tu-nombre/breve-descripcion
```

Después abres el Pull Request en GitHub (la URL te la da el propio `git push`).

## 3. Normas anti-conflictos

- **Un archivo, un responsable.** Si vas a tocar un archivo que no es tuyo, avisa por el PR antes.
- **Nada de `git push --force`** (está bloqueado en `main` y desaconsejado en tus ramas: fuerza a los demás a reescribir su historial).
- **Nada de `git commit --amend`** después de haber hecho `push`.
- **Sincroniza a menudo:** `git fetch origin` + `git rebase origin/main` en tu rama antes del PR.
- **No edites `gradle.properties`, `build.gradle` ni `fabric.mod.json` a la vez.** Cambios de versiones van en un PR aparte y lo revisan ambos.
- **Textos nuevos, archivos nuevos.** Cada nueva funcionalidad debe estar en su propio archivo Java; así dos personas casi nunca tocan el mismo fichero.
- `.gitattributes` fuerza finales de línea LF para que no haya conflictos raros entre Windows y Linux.

## 4. Fusionar

- Se usa **Squash and merge**: el historial de `main` queda lineal y limpio.
- El PR debe pasar el check de GitHub Actions `build` (compila con `./gradlew build`).
- Se borra la rama después de fusionar.

## 5. Si aparece un conflicto

No lo fuerces. En tu rama:

```bash
git fetch origin
git rebase origin/main
# git te marca los archivos en conflicto -> los editas, luego:
git add .
git rebase --continue
git push --force-with-lease   # solo en TU rama, nunca en main
```

Y coméntalo en el PR para que el otro lo vea.