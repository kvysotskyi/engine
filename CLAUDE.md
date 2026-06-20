# CLAUDE.md — Open Integration Engine

## Build

This project uses **Apache Ant** (not Maven/Gradle).

```bash
# 1. Build donkey (shared framework — must come first)
cd donkey && ant build

# 2. Compile server source + produce all extension JARs
cd server && ant -f mirth-build.xml build-server-extensions

# 3. Compile client source + produce client-side extension JARs
ant -f mirth-build.xml build-client
```

`build-server-extensions` also copies `*-shared.jar` files to `client/lib/` so
the client compiler can see the shared model classes before the client is built.

The full orchestrator is `server/mirth-build.xml`. Individual module build files:
- `donkey/build.xml`
- `server/build.xml` (targets: `compile`, `create-connectors`, `create-plugins`)
- `client/ant-build.xml` (targets: `compile`, `build`)
