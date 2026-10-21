package me.bechberger.webdemo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;

import java.util.*;

/**
 * Static helpers for PSI operations used by the Class Browser.
 *
 * <p>All methods that read PSI data wrap their work in
 * {@code ApplicationManager.getApplication().runReadAction(Computable)} so they are safe to call
 * from any thread (including Javalin worker threads). Navigation dispatches to the EDT via
 * {@code invokeLater}.
 */
public class PsiHelper {

    public record ClassInfo(String fqn, String simpleName, List<String> methods) {}

    /** Returns up to {@code limit} classes whose FQN or any method name contains {@code filter}. */
    public static List<ClassInfo> listClasses(Project project, String filter, int limit) {
        String lower = filter.toLowerCase();
        return ApplicationManager.getApplication().runReadAction((Computable<List<ClassInfo>>) () -> {
            List<ClassInfo> result = new ArrayList<>();
            // Use iterable + break — Query.forEach() does not short-circuit on false in IntelliJ's API
            for (PsiClass klass : AllClassesSearch.search(GlobalSearchScope.projectScope(project), project)) {
                if (result.size() >= limit) break;
                if (matches(klass, lower)) result.add(toClassInfo(klass));
            }
            return result;
        });
    }

    /** Returns source text and the 1-based line of {@code methodName} in one read action. */
    public record SourceResult(String source, int line) {}

    public static SourceResult getSourceWithLine(Project project, String fqn, String methodName) {
        return ApplicationManager.getApplication().runReadAction((Computable<SourceResult>) () -> {
            PsiClass klass = findClass(project, fqn);
            if (klass == null) return null;
            PsiFile file = klass.getContainingFile();
            if (file == null || file.getVirtualFile() == null) return null;
            if ("class".equals(file.getVirtualFile().getExtension())) return null; // decompiled
            String text = file.getText();
            int line = 1;
            if (methodName != null) {
                PsiMethod[] methods = klass.findMethodsByName(methodName, false);
                if (methods.length > 0) {
                    int offset = methods[0].getTextOffset();
                    for (int i = 0; i < offset && i < text.length(); i++) {
                        if (text.charAt(i) == '\n') line++;
                    }
                }
            }
            return new SourceResult(text, line);
        });
    }

    /** Navigates the editor to {@code fqn}. Must be called from any thread — uses invokeLater. */
    public static void navigate(Project project, String fqn) {
        ApplicationManager.getApplication().runReadAction(() -> {
            PsiClass klass = findClass(project, fqn);
            if (klass == null) return;
            PsiFile file = klass.getContainingFile();
            if (file == null || file.getVirtualFile() == null) return;
            int offset = klass.getTextOffset();
            ApplicationManager.getApplication().invokeLater(() ->
                new OpenFileDescriptor(project, file.getVirtualFile(), offset)
                    .navigate(true)
            );
        });
    }

    /** Returns the FQN of the class at the editor caret, or null if none. */
    public static String classNameAtCaret(com.intellij.openapi.editor.Editor editor, Project project) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
            if (file == null) return null;
            int offset = editor.getCaretModel().getOffset();
            PsiElement element = file.findElementAt(offset);
            PsiClass klass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (klass != null) return klass.getQualifiedName();
            // Also handle import statements
            PsiImportStatement imp = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
            return imp != null ? imp.getQualifiedName() : null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean matches(PsiClass klass, String lower) {
        if (lower.isEmpty()) return true;
        String fqn = klass.getQualifiedName();
        if (fqn == null) return false;
        if (fqn.toLowerCase().contains(lower)) return true;
        return Arrays.stream(klass.getMethods())
            .anyMatch(m -> m.getName().toLowerCase().contains(lower));
    }

    private static ClassInfo toClassInfo(PsiClass klass) {
        String fqn = Objects.requireNonNullElse(klass.getQualifiedName(), klass.getName());
        String simple = Objects.requireNonNullElse(klass.getName(), fqn);
        List<String> methods = Arrays.stream(klass.getMethods())
            .filter(m -> m.hasModifierProperty(PsiModifier.PUBLIC))
            .map(PsiMethod::getName)
            .distinct()
            .toList();
        return new ClassInfo(fqn, simple, methods);
    }

    private static PsiClass findClass(Project project, String fqn) {
        return JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.allScope(project));
    }
}
