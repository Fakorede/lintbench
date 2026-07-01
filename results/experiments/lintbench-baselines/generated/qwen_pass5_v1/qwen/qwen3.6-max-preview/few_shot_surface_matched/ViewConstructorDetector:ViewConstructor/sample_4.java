package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String VIEW_CLASS = "android.view.View";

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Some layout tools (such as the Android layout editor) need to find a "
                            + "constructor with one of the following signatures:\n"
                            + "* `View(Context context)`\n"
                            + "* `View(Context context, AttributeSet attrs)`\n"
                            + "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                            + "If your custom view needs to perform initialization which does "
                            + "not apply when used in a layout editor, you can surround the "
                            + "given code with a check to see if `View#isInEditMode()` is "
                            + "false, since that method will return `false` at runtime but "
                            + "true within a user interface editor.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION)
            .setAndroidSpecific(true);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (VIEW_CLASS.equals(qualifiedName) || declaration.isAbstract()) {
            return;
        }

        boolean hasValidConstructor = false;
        for (PsiMethod method : declaration.getMethods()) {
            if (method.isConstructor() && isValidViewConstructor(context, method)) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor "
                            + "used by tools: `(Context)` or `(Context,AttributeSet)` or "
                            + "`(Context,AttributeSet,int)`");
        }
    }

    private boolean isValidViewConstructor(JavaContext context, PsiMethod method) {
        PsiParameter[] params = method.getParameterList().getParameters();
        int count = params.length;
        if (count < 1 || count > 3) {
            return false;
        }

        if (!context.getEvaluator().typeMatches(params[0].getType(), "android.content.Context")) {
            return false;
        }
        if (count >= 2) {
            if (!context.getEvaluator().typeMatches(params[1].getType(), "android.util.AttributeSet")) {
                return false;
            }
        }
        if (count == 3) {
            String type3 = params[2].getType().getCanonicalText();
            if (!"int".equals(type3) && !"java.lang.Integer".equals(type3)) {
                return false;
            }
        }
        return true;
    }
}