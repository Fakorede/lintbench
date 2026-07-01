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
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.isAbstract()) {
            return;
        }

        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor()) {
                List<UParameter> parameters = method.getUastParameters();
                if (isValidViewConstructor(parameters)) {
                    return;
                }
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `" + declaration.getName()
                        + "` is missing constructor used by tools: `(Context)` or "
                        + "`(Context,AttributeSet)` or `(Context,AttributeSet,int)`");
    }

    private boolean isValidViewConstructor(@NonNull List<UParameter> parameters) {
        int size = parameters.size();
        if (size < 1 || size > 3) {
            return false;
        }

        String type0 = parameters.get(0).getType().getCanonicalText();
        if (!"android.content.Context".equals(type0)) {
            return false;
        }

        if (size == 1) {
            return true;
        }

        String type1 = parameters.get(1).getType().getCanonicalText();
        if (!"android.util.AttributeSet".equals(type1)) {
            return false;
        }

        if (size == 2) {
            return true;
        }

        String type2 = parameters.get(2).getType().getCanonicalText();
        return "int".equals(type2);
    }
}