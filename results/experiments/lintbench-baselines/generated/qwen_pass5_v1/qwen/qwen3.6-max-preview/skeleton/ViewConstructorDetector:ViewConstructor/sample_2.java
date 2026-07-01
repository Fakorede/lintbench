package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Some layout tools (such as the Android layout editor) need to find a constructor with one of the following signatures:\n" +
                    "* `View(Context context)`\n" +
                    "* `View(Context context, AttributeSet attrs)`\n" +
                    "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
                    "If your custom view needs to perform initialization which does not apply when used in a layout editor, you can surround the given code with a check to see if `View#isInEditMode()` is false, since that method will return `false` at runtime but true within a user interface editor.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifier(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor()) {
                List<UParameter> params = method.getUastParameters();
                if (isValidConstructor(params)) {
                    hasValidConstructor = true;
                    break;
                }
            }
        }

        if (!hasValidConstructor) {
            String message = String.format(
                    "Custom view `%1$s` is missing constructor used by tools: `(Context)` or `(Context,AttributeSet)` or `(Context,AttributeSet,int)`",
                    declaration.getName());
            context.report(ISSUE, context.getLocation(declaration), message);
        }
    }

    private static boolean isValidConstructor(List<UParameter> params) {
        int size = params.size();
        if (size == 1) {
            return isContext(params.get(0));
        } else if (size == 2) {
            return isContext(params.get(0)) && isAttributeSet(params.get(1));
        } else if (size == 3) {
            return isContext(params.get(0)) && isAttributeSet(params.get(1)) && isInt(params.get(2));
        }
        return false;
    }

    private static boolean isContext(UParameter param) {
        return "android.content.Context".equals(param.getType().getCanonicalText());
    }

    private static boolean isAttributeSet(UParameter param) {
        return "android.util.AttributeSet".equals(param.getType().getCanonicalText());
    }

    private static boolean isInt(UParameter param) {
        return "int".equals(param.getType().getCanonicalText());
    }
}