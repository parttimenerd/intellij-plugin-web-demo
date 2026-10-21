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
 * {@link ClassBrowserServer#broadcastFilter(String)}. Only visible when the server is running
 * and the event source is an editor.
 */
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
