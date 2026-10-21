package me.bechberger.webdemo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class ClassBrowserToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ClassBrowserServer server = ClassBrowserServer.getInstance(project);
        // Pass toolWindow.getDisposable() so the browser is disposed when the tool window closes,
        // not when the project closes — avoids a stale retry loop if the window is reopened.
        BrowserWindow browser = new BrowserWindow("http://localhost:" + server.getPort() + "/", toolWindow.getDisposable());
        var content = ContentFactory.getInstance().createContent(browser.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
