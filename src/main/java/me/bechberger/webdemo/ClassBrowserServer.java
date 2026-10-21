package me.bechberger.webdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

public class ClassBrowserServer implements Disposable {

    private static final int MAX_RESULTS = 50;
    private static final Logger LOG = Logger.getLogger(ClassBrowserServer.class.getName());

    private final Project project;
    private final Javalin app;
    private final int port;
    private final Set<WsContext> clients = new CopyOnWriteArraySet<>();
    private final ObjectMapper json = new ObjectMapper();
    private volatile boolean running = false;

    public ClassBrowserServer(Project project) {
        this.project = project;
        this.port = freePort();
        // Javalin resolves SLF4J via the context classloader — swap to its own to avoid
        // NoClassDefFoundError inside IntelliJ's plugin classloader (mirrors Server.java in jfrtofp-server)
        ClassLoader saved = Thread.currentThread().getContextClassLoader();
        ClassLoader javalinCl = Javalin.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(javalinCl != null ? javalinCl : ClassLoader.getSystemClassLoader());
        try {
            this.app = Javalin.create(config ->
                config.staticFiles.add("/web", io.javalin.http.staticfiles.Location.CLASSPATH)
            ).start(port);
            registerRoutes(); // must be inside the classloader swap — Jetty handler setup needs it
            running = true;
        } finally {
            Thread.currentThread().setContextClassLoader(saved);
        }
        LOG.info("Class Browser running on port " + port);
    }

    public static ClassBrowserServer getInstance(Project project) {
        return project.getService(ClassBrowserServer.class);
    }

    public int getPort() { return port; }

    public boolean isRunning() { return running; }

    public void broadcastFilter(String text) {
        String msg;
        try { msg = json.writeValueAsString(Map.of("filter", text)); }
        catch (Exception e) { return; }
        for (WsContext ctx : clients) {
            try { ctx.send(msg); } catch (Exception ignored) {}
        }
    }

    private void registerRoutes() {
        app.get("/api/classes", ctx -> {
            String filter = Objects.requireNonNullElse(ctx.queryParam("filter"), "");
            List<PsiHelper.ClassInfo> classes = PsiHelper.listClasses(project, filter, MAX_RESULTS);
            ctx.json(classes);
        });

        app.get("/api/source", ctx -> {
            String fqn = ctx.queryParam("fqn");
            if (fqn == null) { ctx.status(400); return; }
            String method = ctx.queryParam("method");
            PsiHelper.SourceResult result = PsiHelper.getSourceWithLine(project, fqn, method);
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
        });
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
        catch (IOException e) { throw new RuntimeException("No free port", e); }
    }

    @Override
    public void dispose() { running = false; app.stop(); }
}
