package me.bechberger.webdemo;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SetFilterAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean visible = project != null
            && ClassBrowserServer.getInstance(project).isRunning()
            && e.getData(CommonDataKeys.EDITOR) != null;
        e.getPresentation().setVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        var editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;
        String fqn = PsiHelper.classNameAtCaret(editor, project);
        if (fqn == null) return;
        ClassBrowserServer.getInstance(project).broadcastFilter(fqn);
    }
}
