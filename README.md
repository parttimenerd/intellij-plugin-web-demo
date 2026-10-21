# IntelliJ Web Demo

A conference-talk blueprint showing how to embed a modern web frontend inside an IntelliJ
plugin. Implements a Class Browser: search project classes and methods, view source,
navigate — with an IDE→web push via WebSocket.

Talk: *"Integrating a web-based profiler into IntelliJ"*

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
              ├── WS  /ws/filter    ◀── broadcastFilter()               │ click class name
              └── GET /             ──▶ classpath:/web/ (Vite)    POST /api/navigate

    BrowserWindow (JCEF — must be Java, known IntelliJ/Kotlin bug)
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

## Design decisions

| Decision | Why |
|----------|-----|
| Project service, not singleton | Tied to project lifecycle; no stale `Project` reference |
| `projectScope` not `allScope` | Avoids scanning 50k+ library classes |
| `CopyOnWriteArraySet` for WS clients | Thread-safe add/remove from connect/close threads |
| HTTP for web→IDE | Stateless, easy to reason about |
| WebSocket for IDE→web | Fire-and-forget push, no polling |
| `runReadAction(Computable)` | Java needs explicit cast for the return-value overload |
| JCEF wrapper in Java | IntelliJ bug: JBCefBrowserBuilder misbehaves from Kotlin |
| `null` source if decompiled | Don't show bytecode as source |
| shiki for syntax highlighting | Self-contained HTML with inline styles — no extra CSS |
