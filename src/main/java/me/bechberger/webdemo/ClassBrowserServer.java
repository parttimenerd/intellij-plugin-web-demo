package me.bechberger.webdemo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Javalin HTTP/WebSocket server that backs the Class Browser tool window.
 *
 * <p>Registered as a project service so IntelliJ manages its lifecycle. Routes:
 * <ul>
 *   <li>{@code GET /api/classes?filter=} — list matching project classes with public methods</li>
 *   <li>{@code GET /api/source?fqn=&amp;method=} — return source text and target line number</li>
 *   <li>{@code POST /api/navigate?fqn=} — jump editor to the class</li>
 *   <li>{@code GET /api/theme} — returns {@code "dark"} or {@code "light"} per IDE theme</li>
 *   <li>{@code WS /ws/filter} — push filter/reload messages to connected web clients (IDE→web)</li>
 *   <li>{@code GET /} — serve the Vite-built SPA from {@code classpath:/web/}</li>
 * </ul>
 *
 * <p>The classloader is temporarily swapped to {@code Javalin.class.getClassLoader()} before
 * {@code start()} to avoid SLF4J {@code NoClassDefFoundError} inside IntelliJ's plugin classloader.
 */
public class ClassBrowserServer implements Disposable {

    private static final int MAX_RESULTS = 50;

    private final Project project;
    private final Javalin app;
    private final int port;
    private final Set<WsContext> clients = new CopyOnWriteArraySet<>();
    private final Timer reloadTimer = new Timer("class-browser-reload", true);
    private volatile TimerTask pendingReload;

    public ClassBrowserServer(Project project) {
        this.project = project;
        this.port = freePort();
        // Javalin resolves SLF4J via the context classloader — swap to its own to avoid
        // NoClassDefFoundError inside IntelliJ's plugin classloader (mirrors Server.java in jfrtofp-server)

        // <editor-fold desc="Javalin classloader swap">
        ClassLoader saved = Thread.currentThread().getContextClassLoader();
        ClassLoader javalinCl = Javalin.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(javalinCl != null ? javalinCl : ClassLoader.getSystemClassLoader());
        try {
            this.app = Javalin.create(config ->
                config.staticFiles.add("/web", io.javalin.http.staticfiles.Location.CLASSPATH)
            ).start(port);
            registerRoutes(); // must be inside the classloader swap — Jetty handler setup needs it
        } finally {
            Thread.currentThread().setContextClassLoader(saved);
        }
        // </editor-fold>
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override public void childAdded(PsiTreeChangeEvent e)    { scheduleReload(); }
            @Override public void childRemoved(PsiTreeChangeEvent e)  { scheduleReload(); }
            @Override public void childReplaced(PsiTreeChangeEvent e) { scheduleReload(); }
        }, this);
    }

    public static ClassBrowserServer getInstance(Project project) {
        return project.getService(ClassBrowserServer.class);
    }

    public int getPort() { return port; }

    public void broadcastFilter(String fqn) {
        String msg = "{\"filter\":\"" + fqn.replace("\"", "\\\"") + "\"}";
        broadcast(msg);
    }

    public void broadcastReload() {
        broadcast("{\"reload\":true}");
    }

    private void broadcast(String msg) {
        for (WsContext ctx : clients) {
            try { ctx.send(msg); } catch (Exception ignored) {}
        }
    }

    private void registerRoutes() {
        app.get("/api/theme", ctx ->
            ctx.result(StartupUiUtil.INSTANCE.isDarkTheme() ? "dark" : "light")
        );

        app.get("/api/classes", ctx -> {
            String filter = Objects.requireNonNullElse(ctx.queryParam("filter"), "");
            ctx.json(PsiHelper.listClasses(project, filter, MAX_RESULTS));
        });

        app.get("/api/source", ctx -> {
            String fqn = ctx.queryParam("fqn");
            if (fqn == null) { ctx.status(400); return; }
            PsiHelper.SourceResult result = PsiHelper.getSourceWithLine(project, fqn, ctx.queryParam("method"));
            if (result == null) { ctx.status(404).result("Source not available"); return; }
            ctx.json(Map.of("source", result.source(), "line", result.line()));
        });

        app.post("/api/navigate", ctx -> {
            String fqn = ctx.queryParam("fqn");
            if (fqn == null) { ctx.status(400); return; }
            PsiHelper.navigate(project, fqn);
            ctx.status(200);
        });

        app.ws("/ws/filter", ws -> {
            ws.onConnect(clients::add);
            ws.onClose(clients::remove);
            ws.onError(ctx -> clients.remove(ctx));
        });
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
        catch (IOException e) { throw new RuntimeException("No free port", e); }
    }

    private void scheduleReload() {
        if (pendingReload != null) pendingReload.cancel();
        pendingReload = new TimerTask() { public void run() { broadcastReload(); } };
        reloadTimer.schedule(pendingReload, 500);
    }

    @Override
    public void dispose() {
        reloadTimer.cancel();
        app.stop();
    }
}
