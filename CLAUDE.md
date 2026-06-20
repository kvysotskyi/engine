# CLAUDE.md — Open Integration Engine

Developer guide for AI-assisted work on this codebase.

---

## Build System

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
the client compiler can see the shared model classes.

The full orchestrator is `server/mirth-build.xml`. Individual module build files:
- `donkey/build.xml`
- `server/build.xml` (targets: `compile`, `create-connectors`, `create-plugins`)
- `client/ant-build.xml` (targets: `compile`, `build`)

---

## Adding a New Connector

Every connector spans **three** modules and **two** build files.

### 1. Shared model (`server/src/…`)

Create `<Name>DispatcherProperties.java` (or `ReceiverProperties`) extending
`ConnectorProperties`. Must implement `DestinationConnectorPropertiesInterface`
(or `SourceConnectorPropertiesInterface`). Required overrides:
`getProtocol()`, `getName()`, `toFormattedString()`, `clone()`,
`canValidateResponse()`, `equals()`, all `migrateX_Y_Z()` stubs,
`getPurgedProperties()`.

Copy constructors: `ListenerConnectorProperties` and `SourceConnectorProperties`
have **no copy constructor** — must copy fields individually:

```java
ListenerConnectorProperties src = props.getListenerConnectorProperties();
listenerConnectorProperties = new ListenerConnectorProperties(src.getPort());
listenerConnectorProperties.setHost(src.getHost());
```

### 2. Server-side dispatcher/receiver (`server/src/…`)

Extends `DestinationConnector` or `SourceConnector` from donkey. Key lifecycle:
`onDeploy` → `onStart` → `send`/`handleRecoveredResponse` → `onStop`/`onHalt` → `onUndeploy`.

### 3. Client-side UI panel (`client/src/…`)

Extends `ConnectorSettingsPanel`. Must implement:
- `getConnectorName()` — returns `new <Name>Properties().getName()`
- `getProperties()` / `setProperties()` — read from / write to UI fields
- `getDefaults()` — returns `new <Name>Properties()`
- `checkProperties()` / `resetInvalidProperties()`
- `getConnectorTypeDecoration()` — `new ConnectorTypeDecoration(Mode.DESTINATION)`

Use **MigLayout** (`"novisualpadding, hidemode 3, insets 10, gap 4"`) for panels.
Use `hidemode 3` so hidden components don't take space.

UI component notes:
- `MirthSyntaxTextArea` extends jEdit's `JEditTextArea` — **not** RSyntaxTextArea.
  Do not call `setSyntaxEditingStyle()`.
- `MirthIconTextField`, `MirthTextField`, `MirthPasswordField` are standard
  Mirth-branded Swing wrappers.

### 4. Connector descriptor XML

Source: `server/src/com/mirth/connect/connectors/<package>/<name>-destination.xml`

```xml
<connectorMetaData path="<extension-folder-name>">
    <name>Human Readable Name</name>
    <author>NextGen Healthcare</author>
    <pluginVersion>@mirthversion</pluginVersion>
    <mirthVersion>@mirthversion</mirthVersion>
    <description>…</description>
    <clientClassName>com.mirth.connect.connectors.<pkg>.<UISender></clientClassName>
    <serverClassName>com.mirth.connect.connectors.<pkg>.<Dispatcher></serverClassName>
    <sharedClassName>com.mirth.connect.connectors.<pkg>.<Properties></sharedClassName>
    <library type="CLIENT" path="<ext>-client.jar" />
    <library type="SHARED" path="<ext>-shared.jar" />
    <library type="SERVER" path="<ext>-server.jar" />
    <transformers></transformers>
    <protocol>UNIQUE_PROTOCOL_ID</protocol>
    <type>DESTINATION</type>
</connectorMetaData>
```

The extension loader picks up **any** file ending in `destination.xml` or
`source.xml` inside the extension directory — so a single directory can host
multiple connectors (e.g., `dicomweb` hosts STOW-RS, QIDO-RS, and WADO-RS).

### 5. Wire into both build files

**`server/build.xml`** — `init` target (property) + `create-connectors` target (JAR tasks):

```xml
<!-- init target -->
<property name="connectors.myext" value="${extensions}/myext" />

<!-- create-connectors target -->
<mkdir dir="${connectors.myext}" />
<copy todir="${connectors.myext}">
    <fileset dir="${src}/com/mirth/connect/connectors/myext">
        <include name="*.xml" />
    </fileset>
</copy>
<!-- optional: copy extra runtime JARs -->
<copy todir="${connectors.myext}/lib" failonerror="false" quiet="true">
    <fileset dir="${lib.extensions}/myext" />
</copy>
<jar destfile="${connectors.myext}/myext-shared.jar" basedir="${classes}" …>
    <include name="com/mirth/connect/connectors/myext/*Properties.class" />
    <include name="com/mirth/connect/connectors/myext/*Properties$*.class" />
</jar>
<jar destfile="${connectors.myext}/myext-server.jar" …>
    <fileset dir="${classes}">
        <include name="com/mirth/connect/connectors/myext/**" />
        <exclude name="com/mirth/connect/connectors/myext/*Properties.class" />
        <exclude name="com/mirth/connect/connectors/myext/*Properties$*.class" />
    </fileset>
</jar>
```

**`client/ant-build.xml`** — same property in `init`, plus in `build` target:

```xml
<mkdir dir="${connectors.myext}" />
<jar destfile="${connectors.myext}/myext-client.jar" basedir="${classes}" …>
    <include name="com/mirth/connect/connectors/myext/**" />
</jar>
```

Shared JARs must include **all inner/nested classes** via `$*.class` patterns —
otherwise the client compiler fails to find enum types declared inside properties
classes.

---

## DICOM Connectors

### Legacy C-STORE (dimse package)

| File | Role |
|------|------|
| `server/src/com/mirth/connect/connectors/dimse/DICOMReceiver.java` | C-STORE SCP source connector |
| `server/src/com/mirth/connect/connectors/dimse/DICOMReceiverProperties.java` | Properties (+ `acceptedSopClasses`, `acceptedTransferSyntaxes`, `storageFolder`, `maxConnections`) |
| `server/src/org/dcm4che2/tool/dcmrcv/MirthDcmRcv.java` | Custom dcm4che2 SCP — handles file-based storage, async dispatch, custom SOP/TS filtering |

**File-based storage mode**: set `storageFolder` → SCP writes `.dcm` to disk and
acknowledges immediately; Mirth pipeline runs asynchronously; file is deleted
after processing via `dcmrcv.onDispatchComplete(messageId)`.

**Custom SOP class filtering**: `MirthDcmRcv.setCustomSopClasses(String[] cuids)`
replaces the ~50 hardcoded default SOP classes. Overrides `initTransferCapability()`
and `setTransferSyntax()` to intercept the dcm4che2 private `tsuids` field.

### C-FIND SCU/SCP (dicomquery package)

| File | Role |
|------|------|
| `DICOMQueryDispatcher.java` | C-FIND SCU destination — sends queries to remote DICOM QR SCP |
| `DICOMQueryDispatcherProperties.java` | Query model, host/port, AE titles, timeouts |
| `DICOMQueryReceiver.java` | C-FIND SCP source — listens for inbound C-FIND queries |
| `DICOMQueryReceiverProperties.java` | Port, AE title, accepted model, timeouts |
| `MirthCFindService.java` | dcm4che2 DimseRSPHandler that dispatches each C-FIND result as a Mirth message |

Response from `DICOMQueryDispatcher`: JSON array of DICOM attribute objects, one per
matching result. Each object maps DICOM keyword → value.

Extension runtime needs dcm4che-core and dcm4che-net JARs — copied from
`server/lib/extensions/dimse/` during the build.

### DICOMweb (dicomweb package)

Three destination connectors share one extension directory and auth helpers:

| Connector | Protocol | URL pattern | Response |
|-----------|----------|-------------|----------|
| **STOW-RS** (`DICOMWebDispatcher`) | `DICOMWEBSTOW` | `POST {base}/studies` | HTTP status |
| **QIDO-RS** (`QIDORSDispatcher`) | `DICOMWEBQIDO` | `GET {base}/studies[/{uid}/series[/{uid}/instances]]` | JSON/XML array |
| **WADO-RS** (`WADORSDispatcher`) | `DICOMWEBWADO` | `GET {base}/studies/{uid}[/series/{uid}[/instances/{uid}]]` | saves `.dcm` files, returns JSON path array |

#### Auth helpers (shared by all three)

| Class | Auth type |
|-------|-----------|
| `GoogleAuthHelper` | Google Cloud service account JSON key → OAuth2 bearer token |
| `AzureAuthHelper` | Azure AD client credentials flow → bearer token |
| `AwsSigV4Helper` | AWS Signature Version 4 — signs any `HttpRequestBase` (GET or POST) |

`AwsSigV4Helper.sign(HttpRequestBase, ...)` works for GET (empty body, no
`Content-Type` in canonical headers) and POST (with body and `Content-Type`).

Supported auth types in all three connectors:
`NONE`, `BASIC`, `BEARER`, `GOOGLE_SERVICE_ACCOUNT`, `AZURE_SERVICE_PRINCIPAL`, `AWS_SIG_V4`

#### WADO-RS specifics

- Sends `Accept: multipart/related; type="application/dicom"`
- Parses multipart response with a pure-Java byte-array boundary search
  (no external MIME library needed)
- Names each saved file `{SOPInstanceUID}.dcm` by reading tag `(0008,0018)` from
  the raw DICOM Part 10 bytes via a minimal explicit-VR parser (no dcm4che2 on classpath)
- Falls back to `instance_NNNN.dcm` if parsing fails
- Response message: JSON array of absolute file paths, e.g.
  `["/dicom/out/1.2.840.xxx.dcm", "/dicom/out/1.2.840.yyy.dcm"]`
- Default read timeout: **120 s** (vs 60 s for QIDO-RS) for large bulk retrieval

---

## DataPruner Integration

`server/src/com/mirth/connect/plugins/datapruner/` — extended to delete DICOM files
from disk when messages are pruned. If the message content is a file path ending in
`.dcm` (written by the file-based STOW SCP or WADO-RS retriever), the pruner
deletes the file before removing the database row.

---

## Runtime Dependencies

| Package | Extra JARs needed at runtime |
|---------|------------------------------|
| `dimse` | `server/lib/extensions/dimse/` — dcm4che2 core/net/tools |
| `dicomquery` | Same dcm4che JARs (copied from dimse during build) |
| `dicomweb` | None — uses Apache HttpComponents already in server classpath |

---

## Key Conventions

- **Template replacement**: call `replacer.replaceValues(value, connectorMessage)` in
  `replaceConnectorProperties()` for every user-editable string field.
- **Token caching**: Google and Azure tokens are cached in `volatile` fields and
  refreshed when `token.isExpired()`. Methods are `synchronized`.
- **HTTP client lifecycle**: create in `onStart()`, close in both `onStop()` and
  `onHalt()`. Null-check before closing.
- **Status mapping**:  HTTP 200 → `SENT`, HTTP 204 → `SENT` (empty result),
  HTTP 5xx → `QUEUED` (retry), HTTP 4xx → `ERROR`.
- **`$*.class` in shared JARs**: always include inner/nested class files for any
  enum or inner class declared inside a Properties class, or the client build fails.
