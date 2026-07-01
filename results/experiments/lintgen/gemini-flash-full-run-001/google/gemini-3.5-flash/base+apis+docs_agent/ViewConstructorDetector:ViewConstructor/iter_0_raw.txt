package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "ViewConstructor",
        "Missing View constructors for XML inflation",
        "Some layout tools (such as the Android layout editor) need to find a constructor with one of the following signatures:\n" +
        " * `View(Context context)`\n" +
        " * `View(Context context, AttributeSet attrs)`\n" +
        " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
        "\n" +
        "If your custom view needs to perform initialization which does not apply when used in a layout editor, you can surround the given code with a check to see if `View#isInEditMode()` is false, since that method will return `false` at runtime but true within a user interface editor.",
        Category.USABILITY,
        3,
        Severity.WARNING,
        new Implementation(
            ViewConstructorDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || declaration.getPsi().isAnnotationType()) {
            return;
        }

        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (context.getEvaluator().isPrivate(declaration)) {
            return;
        }

        if (declaration.getContainingClass() != null && !context.getEvaluator().isStatic(declaration)) {
            return;
        }

        UMethod[] constructors = declaration.getMethods();
        boolean hasConstructor = false;
        boolean hasValidConstructor = false;

        for (UMethod method : constructors) {
            if (method.isConstructor()) {
                hasConstructor = true;
                if (isValidSignature(context, method)) {
                    hasValidConstructor = true;
                    break;
                }
            }
        }

        if (hasConstructor && !hasValidConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `" + declaration.getName() + "` is missing recommended constructors"
            );
        }
    }

    private boolean isValidSignature(JavaContext context, UMethod method) {
        List<UParameter> parameters = method.getUastParameters();
        int size = parameters.size();
        if (size < 1 || size > 4) {
            return false;
        }

        if (!isType(context, parameters.get(0), "android.content.Context")) {
            return false;
        }

        if (size >= 2) {
            if (!isType(context, parameters.get(1), "android.util.AttributeSet")) {
                return false;
            }
        }

        if (size >= 3) {
            if (!isType(context, parameters.get(2), "int")) {
                return false;
            }
        }

        if (size == 4) {
            if (!isType(context, parameters.get(3), "int")) {
                return false;
            }
        }

        return true;
    }

    private boolean isType(JavaContext context, UParameter parameter, String typeName) {
        PsiType type = parameter.getType();
        if (type instanceof PsiPrimitiveType) {
            return type.getCanonicalText().equals(typeName);
        }
        return context.getEvaluator().typeMatches(type, typeName);
    }
}