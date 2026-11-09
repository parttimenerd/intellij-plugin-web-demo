package me.bechberger.webdemo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;

import javax.swing.*;

/**
 * Wraps JBCefBrowser and hosts it inside a Swing component.
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
     * Poll until the URL sticks, then stop. Wait 500 ms between each loadURL call so
     * we do not interrupt an in-progress navigation (which would cause a blank source pane
     * on initial display because the Vue onMounted fetch gets wiped by the reload).
     */
    private void startRetryLoop() {
        Thread t = new Thread(() -> {
            long lastLoad = System.currentTimeMillis();
            browser.getCefBrowser().loadURL(url);
            while (!browser.isDisposed()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                String current = browser.getCefBrowser().getURL();
                if (current != null && current.startsWith("http://localhost:")) break;
                if (System.currentTimeMillis() - lastLoad >= 500) {
                    browser.getCefBrowser().loadURL(url);
                    lastLoad = System.currentTimeMillis();
                }
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
