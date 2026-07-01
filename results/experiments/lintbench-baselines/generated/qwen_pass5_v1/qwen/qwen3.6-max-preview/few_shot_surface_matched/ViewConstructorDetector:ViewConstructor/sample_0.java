package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
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
                    "Missing View Constructor",
                    "Some layout tools (such as the Android layout editor) need to find a "
                            + "constructor with one of the following signatures:\n"
                            + "* `View(Context context)`\n"
                            + "* `View(Context context, AttributeSet attrs)`\n"
                            + "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                            + "If your custom view needs to perform initialization which does not apply "
                            + "when used in a layout editor, you can surround the given code with a check "
                            + "to see if `View#isInEditMode()` is false, since that method will return "
                            + "`false` at runtime but true within a user interface editor.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public ViewConstructorDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null || qualifiedName.equals("android.view.View")) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor() && isValidViewConstructor(method, context)) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This custom view should provide a constructor for XML inflation");
        }
    }

    private boolean isValidViewConstructor(@NonNull UMethod constructor, @NonNull JavaContext context) {
        List<UParameter> params = constructor.getValueParameters();
        int count = params.size();
        if (count < 1 || count > 4) {
            return false;
        }

        if (!context.getEvaluator().typeMatches(params.get(0).getType(), "android.content.Context")) {
            return false;
        }
        if (count == 1) {
            return true;
        }

        if (!context.getEvaluator().typeMatches(params.get(1).getType(), "android.util.AttributeSet")) {
            return false;
        }
        if (count == 2) {
            return true;
        }

        if (!params.get(2).getType().equals(PsiType.INT)) {
            return false;
        }
        if (count == 3) {
            return true;
        }

        return params.get(3).getType().equals(PsiType.INT);
    }
}