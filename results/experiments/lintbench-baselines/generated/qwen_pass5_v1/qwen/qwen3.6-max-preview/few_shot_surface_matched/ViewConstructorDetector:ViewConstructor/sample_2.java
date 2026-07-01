package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
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

    public ViewConstructorDetector() {}

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isAbstract() || declaration.isInterface()) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if ("android.view.View".equals(qualifiedName)) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor()) {
                UParameter[] parameters = method.getUastParameters();
                int count = parameters.length;
                if (count >= 1 && count <= 3) {
                    String type0 = parameters[0].getType().getCanonicalText();
                    if (!"android.content.Context".equals(type0)) {
                        continue;
                    }
                    if (count == 1) {
                        hasValidConstructor = true;
                        break;
                    }
                    String type1 = parameters[1].getType().getCanonicalText();
                    if (!"android.util.AttributeSet".equals(type1)) {
                        continue;
                    }
                    if (count == 2) {
                        hasValidConstructor = true;
                        break;
                    }
                    String type2 = parameters[2].getType().getCanonicalText();
                    if ("int".equals(type2)) {
                        hasValidConstructor = true;
                        break;
                    }
                }
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor used by tools: "
                            + "`(Context)`, `(Context, AttributeSet)`, or `(Context, AttributeSet, int)`");
        }
    }
}