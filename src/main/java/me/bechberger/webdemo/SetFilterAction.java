package me.bechberger.webdemo;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Editor right-click → "Browse This Class" action.
 *
 * <p>Reads the fully-qualified name of the class at the caret (or the class named in an import
 * statement) and broadcasts it as a filter to all connected WebSocket clients via
 * {@link ClassBrowserServer#broadcastFilter(String)}. Only visible in editor contexts.
 */
public class SetFilterAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(
            e.getProject() != null && e.getData(CommonDataKeys.EDITOR) != null
        );
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
