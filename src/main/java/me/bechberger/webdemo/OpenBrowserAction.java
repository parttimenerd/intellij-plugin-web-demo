package me.bechberger.webdemo;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class OpenBrowserAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        var toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Class Browser");
        if (toolWindow != null) toolWindow.show();
    }
}
