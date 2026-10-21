# IntelliJ Web Demo

A conference-talk blueprint showing how to embed a modern web frontend inside an IntelliJ
plugin. Implements a Class Browser: search project classes and methods, view source,
navigate — with an IDE→web push via WebSocket.

Talk: *"Integrating a web-based profiler into IntelliJ"*

Based on the real-world [intellij-profiler-plugin (jfrplugin)](https://github.com/parttimenerd/intellij-profiler-plugin),
which embeds the Firefox Profiler and Javalin in IntelliJ. This repo distils the core patterns
into a standalone, maximally readable demo.

## Architecture

    IntelliJ                              localhost:{port}
    ────────────────────────────────────  ──────────────────────────────────
    SetFilterAction                            App.vue (Vue 3 + TypeScript)
    (right-click class or import)
      │ reads class name at caret              filter ──watch──▶ GET /api/classes
      │                                        ▲                        │
      └──▶ ClassBrowserServer ──WS push───────┘              class boxes + method chips
              │  (project service)                                      │ @click
              ├── GET /api/classes  ──▶ PsiHelper.listClasses()   GET /api/source
              ├── GET /api/source   ──▶ PsiHelper.getSourceWithLine()   │
              ├── POST /api/navigate ──▶ PsiHelper.navigate()     source pane (shiki)
              ├── WS  /ws/filter    ──▶ web (App.vue listens, filter.value updated)
              └── GET /             ──▶ classpath:/web/ (Vite)    POST /api/navigate

    BrowserWindow (JCEF — must be Java; reference impl always written in Java for this reason)
      loads http://localhost:{port}/
      retry loop: CEF silently drops the first loadURL call

    Threading
      Javalin worker  →  PsiHelper (runReadAction — any thread safe)
      EDT only        →  navigate  (invokeLater)
      JCEF thread     →  retry loop

    IDE → web   WebSocket push   (SetFilterAction → broadcastFilter)
    web → IDE   HTTP GET/POST    (fetch calls in App.vue)

## Files

| File | Responsibility |
|------|---------------|
| `ClassBrowserServer.java` | Javalin server, project service, WS broadcaster |
| `BrowserWindow.java` | JCEF wrapper with CEF retry loop |
| `ClassBrowserToolWindowFactory.java` | Wires server port → BrowserWindow → ToolWindow |
| `PsiHelper.java` | All PSI: list classes+methods, source, line numbers, navigate, caret class |
| `OpenBrowserAction.java` | Tools menu → show tool window |
| `SetFilterAction.java` | Editor right-click → broadcast filter to frontend |
| `App.vue` | Entire frontend: search, class boxes, source pane, WS |

## Running

```bash
./gradlew runIde
```

Opens a sandbox IntelliJ. Open any Java project inside it, then:

1. **Tools → Open Class Browser** — browser tab opens on the right
2. Type at least 2 characters in the search bar → classes appear
3. Click a method chip → source appears with the method scrolled into view
4. Click a class name → editor jumps to the class
5. Right-click a class name in the editor → **Browse This Class** → search bar updates

## Dependency bundling

IntelliJ Platform Gradle Plugin 2.x automatically bundles all `implementation` dependencies
into the plugin ZIP under `lib/`. No shadow JAR is needed — just declare your dependencies
with `implementation(...)` in `build.gradle.kts`:

```kotlin
implementation("io.javalin:javalin:6.3.0")
implementation("org.slf4j:slf4j-simple:2.0.13")
implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
```

The frontend (Vite build output) is added to the main classpath via `sourceSets.named("main") { resources.srcDir(...) }`,
so it ends up inside the plugin's own JAR at `web/` — exactly where Javalin's static file
handler looks for it (`config.staticFiles.add("/web", Location.CLASSPATH)`).

Verify the ZIP contains your deps before releasing:
```bash
unzip -l build/distributions/*.zip | grep "lib/"
```

## Lessons learned from jfrplugin

These are real issues hit in [intellij-profiler-plugin](https://github.com/parttimenerd/intellij-profiler-plugin)
that this demo is built to avoid from day one.

| Issue | Symptom | Fix |
|-------|---------|-----|
| **JCEF moved to optional bundled plugin (2026.2+)** | `ClassNotFoundException: JBCefBrowserBuilder` on IDEs where `com.intellij.modules.jcef` is absent | Declare `<depends optional="true" config-file="jcef.xml">com.intellij.modules.jcef</depends>` and put all JCEF extension registrations in `jcef.xml` — see `plugin.xml` in this repo |
| **JCEF does not initialise from Kotlin** | `JBCefBrowserBuilder` silently misbehaves or throws when called from Kotlin code | Write the JCEF wrapper in Java — see `BrowserWindow.java` and [WebViewWindow.java](https://github.com/parttimenerd/intellij-profiler-plugin/blob/main/src/main/java/me/bechberger/jfrplugin/editor/WebViewWindow.java) |
| **CEF silently drops first `loadURL`** | Browser stays blank on first open | Daemon thread polls `getURL()` every 10 ms, retries `loadURL` until the URL sticks — see `BrowserWindow.startRetryLoop()` |
| **Javalin classloader conflict** | `NoClassDefFoundError: org/slf4j/…` when Javalin starts inside IntelliJ's plugin classloader | Swap `Thread.currentThread().getContextClassLoader()` to `Javalin.class.getClassLoader()` before `Javalin.create(...).start()`, restore in `finally` — see `ClassBrowserServer` constructor |
| **`Application as Disposable` cast removed (263.x)** | `ClassCastException` when casting `ApplicationManager.getApplication()` to `Disposable` | Use `Runtime.getRuntime().addShutdownHook(...)` instead |
| **Transitive dep missing from JAR** | `NoClassDefFoundError` at runtime for a class from an upstream library | Add the missing dep explicitly with `implementation(...)` — don't assume transitive deps from upstream libs are always shaded |
| **`ProjectActivity` / `startupActivity` EP silently fails** | Startup code never runs, no error thrown | Use `StartupActivity.DumbAware` with `postStartupActivity` extension point |

## Design decisions

| Decision | Why |
|----------|-----|
| Project service, not singleton | Tied to project lifecycle; no stale `Project` reference |
| `projectScope` not `allScope` | Avoids scanning 50k+ library classes |
| `CopyOnWriteArraySet` for WS clients | Thread-safe add/remove from connect/close threads |
| HTTP for web→IDE | Stateless, easy to reason about |
| WebSocket for IDE→web | Fire-and-forget push, no polling |
| `runReadAction(Computable)` | Java needs explicit cast for the return-value overload |
| JCEF wrapper in Java | IntelliJ bug: `JBCefBrowserBuilder` misbehaves from Kotlin |
| `null` source if decompiled | Don't show bytecode as source |
| shiki for syntax highlighting | Self-contained HTML with inline styles — no extra CSS |
| `com.intellij.modules.jcef` optional dep | Prevents `ClassNotFoundException` on 2026.2+ where JCEF moved |
