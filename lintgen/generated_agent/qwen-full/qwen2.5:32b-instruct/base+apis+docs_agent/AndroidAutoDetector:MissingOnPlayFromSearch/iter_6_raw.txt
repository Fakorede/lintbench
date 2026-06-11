package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner {

    private static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "To support voice searches on Android Auto, you need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
            "Supporting voice search in Android Auto requires implementing the `onPlayFromSearch` method. This ensures that your app can respond to voice commands for playing media.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        if (method.getName().equals("onPlayFromSearch")) {
            PsiMethod psiMethod = method.resolve();
            if (psiMethod != null && psiMethod.getParameterList().getParametersCount() == 2) {
                // Method is correctly implemented
                return;
            }
        }

        context.report(ISSUE, method, context.getLocation(method), "Missing or incorrect implementation of `onPlayFromSearch(String query, Bundle bundle)`");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }
}