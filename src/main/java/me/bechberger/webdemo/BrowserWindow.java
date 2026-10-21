package me.bechberger.webdemo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;

import javax.swing.*;

/**
 * Wraps JBCefBrowser. Must be Java — IntelliJ JCEF does not initialise
 * correctly from Kotlin (known platform bug).
 */
public class BrowserWindow implements Disposable {

    private final JBCefBrowser browser;
    private final String url;

    public BrowserWindow(String url, Disposable parent) {
        this.url = url;
        this.browser = new JBCefBrowserBuilder()
            .setEnableOpenDevToolsMenuItem(true)
            .setUrl(url)
            .build();
        // Register with parent so IntelliJ disposes the browser when the project closes.
        // Do NOT also call Disposer.dispose(browser) in dispose() — Disposer is not idempotent
        // and double-dispose causes an assertion error in debug mode.
        Disposer.register(parent, browser);
        startRetryLoop();
    }

    public JComponent getComponent() { return browser.getComponent(); }

    /**
     * CEF silently drops the first loadURL call during initialisation.
     * Poll until the URL sticks, then stop.
     */
    private void startRetryLoop() {
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
                String current = browser.getCefBrowser().getURL();
                if (current != null && current.startsWith("http://localhost:")) break;
                browser.getCefBrowser().loadURL(url);
            }
        }, "browser-retry");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void dispose() {
        // Intentionally empty — browser is disposed via Disposer.register(parent, browser)
    }
}
