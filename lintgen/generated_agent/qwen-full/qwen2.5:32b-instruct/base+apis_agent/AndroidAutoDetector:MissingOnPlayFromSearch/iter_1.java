package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "To support voice searches on Android Auto, you need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
            "This issue reports cases where the method `onPlayFromSearch` is missing in your MediaBrowserService implementation.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, true)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        if ("onPlayFromSearch".equals(method.getName())) {
            PsiMethod psi = (PsiMethod) method.getJavaPsi();
            if (psi.getParameterList().getParametersCount() == 2) {
                // Method is correctly implemented
                return;
            }
        }

        context.report(ISSUE, method, context.getLocation(method),
                "Missing `onPlayFromSearch(String query, Bundle bundle)` implementation");
    }

    @Override
    public void visitClass(JavaContext context, UClass clazz) {
        if (clazz.getQualifiedName().contains("MediaBrowserService")) {
            boolean onPlayFromSearchFound = false;
            for (UMethod method : clazz.getMethods()) {
                if ("onPlayFromSearch".equals(method.getName())) {
                    PsiMethod psi = (PsiMethod) method.getJavaPsi();
                    if (psi.getParameterList().getParametersCount() == 2) {
                        onPlayFromSearchFound = true;
                        break;
                    }
                }
            }

            if (!onPlayFromSearchFound) {
                context.report(ISSUE, clazz, context.getLocation(clazz),
                        "Missing `onPlayFromSearch(String query, Bundle bundle)` implementation in MediaBrowserService");
            }
        }
    }
}