package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.Category.CORRECTNESS;
import static com.android.tools.lint.detector.api.Severity.WARNING;

import androidx.annotation.NonNull;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements Detector.SourceCodeScanner {

    private static final String ISSUE_ID = "ViewConstructor";

    private static final String FQCN_VIEW = "android.view.View";
    private static final String FQCN_CONTEXT = "android.content.Context";
    private static final String FQCN_ATTR_SET = "android.util.AttributeSet";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a "
                    + "constructor with one of the following signatures:\n"
                    + " * `View(Context context)`\n"
                    + " * `View(Context context, AttributeSet attrs)`\n"
                    + " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                    + "If your custom view needs to perform initialization which does not "
                    + "apply when used in a layout editor, you can surround the given code "
                    + "with a check to see if `View#isInEditMode()` is false, since that "
                    + "method will return `false` at runtime but `true` within a user "
                    + "interface editor.",
            CORRECTNESS,
            6,
            WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @NonNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(FQCN_VIEW);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (declaration.getName() == null) {
            return;
        }

        boolean hasContextConstructor =
                hasMatchingConstructor(declaration, FQCN_CONTEXT);
        boolean hasContextAttributeSetConstructor =
                hasMatchingConstructor(declaration, FQCN_CONTEXT, FQCN_ATTR_SET);
        boolean hasContextAttributeSetDefStyleConstructor =
                hasMatchingConstructor(declaration, FQCN_CONTEXT, FQCN_ATTR_SET, "int");

        if (!hasContextConstructor
                && !hasContextAttributeSetConstructor
                && !hasContextAttributeSetDefStyleConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(declaration),
                    "This custom view is missing a constructor that can be used by layout "
                            + "tools for XML inflation; expected one of "
                            + "`View(Context)`, `View(Context, AttributeSet)`, or "
                            + "`View(Context, AttributeSet, int)`"
            );
        }
    }

    private static boolean hasMatchingConstructor(
            @NonNull UClass cls,
            @NonNull String... expectedParameterTypes) {
        for (UMethod method : cls.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }

            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() != expectedParameterTypes.length) {
                continue;
            }

            boolean matches = true;
            for (int i = 0; i < expectedParameterTypes.length; i++) {
                String actualType = parameters.get(i).getType().getCanonicalText();
                if (!expectedParameterTypes[i].equals(actualType)) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                return true;
            }
        }

        return false;
    }
}