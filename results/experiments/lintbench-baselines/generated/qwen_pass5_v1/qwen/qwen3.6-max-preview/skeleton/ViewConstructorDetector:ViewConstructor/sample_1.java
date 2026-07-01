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
import com.intellij.psi.PsiType;
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
                    "Some layout tools (such as the Android layout editor) need to find a " +
                    "constructor with one of the following signatures:\n" +
                    "* `View(Context context)`\n" +
                    "* `View(Context context, AttributeSet attrs)`\n" +
                    "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
                    "If your custom view needs to perform initialization which does " +
                    "not apply when used in a layout editor, you can surround the " +
                    "given code with a check to see if `View#isInEditMode()` is " +
                    "false, since that method will return `false` at runtime but " +
                    "true within a user interface editor.",
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
        if (declaration.isInterface() || declaration.isAbstract() || declaration.getName() == null) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }
            List<UParameter> parameters = method.getParameters();
            int count = parameters.size();
            if (count == 1) {
                if (isContext(parameters.get(0).getType())) {
                    hasValidConstructor = true;
                    break;
                }
            } else if (count == 2) {
                if (isContext(parameters.get(0).getType()) && isAttributeSet(parameters.get(1).getType())) {
                    hasValidConstructor = true;
                    break;
                }
            } else if (count == 3) {
                if (isContext(parameters.get(0).getType()) &&
                    isAttributeSet(parameters.get(1).getType()) &&
                    isInt(parameters.get(2).getType())) {
                    hasValidConstructor = true;
                    break;
                }
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor used by tools: " +
                    "`(Context)`, `(Context,AttributeSet)`, or `(Context,AttributeSet,int)`");
        }
    }

    private static boolean isContext(PsiType type) {
        return type != null && "android.content.Context".equals(type.getCanonicalText());
    }

    private static boolean isAttributeSet(PsiType type) {
        return type != null && "android.util.AttributeSet".equals(type.getCanonicalText());
    }

    private static boolean isInt(PsiType type) {
        return type != null && "int".equals(type.getCanonicalText());
    }
}