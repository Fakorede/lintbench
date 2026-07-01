package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Custom views that are used from XML must provide a constructor that the "
                            + "layout tools can use to instantiate the view. The supported "
                            + "signatures are `View(Context context)`, "
                            + "`View(Context context, AttributeSet attrs)`, and "
                            + "`View(Context context, AttributeSet attrs, int defStyle)`. "
                            + "If a constructor performs initialization that is not needed when "
                            + "used in the layout editor, guard it with a check on "
                            + "`View#isInEditMode()`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface()
                || declaration.isEnum()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)
                || declaration.getName() == null) {
            return;
        }

        if (declaration.getContainingClass() != null
                && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        for (UMethod method : declaration.getMethods()) {
            if (method.isConstructor() && hasValidViewConstructorSignature(method.getParameterList())) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration),
                "This class should provide a constructor that takes a `Context` (and optionally "
                        + "an `AttributeSet` or `int defStyle`) so that layout tools can "
                        + "instantiate it from XML.");
    }

    private static boolean hasValidViewConstructorSignature(PsiParameterList parameterList) {
        int count = parameterList.getParametersCount();
        if (count < 1 || count > 3) {
            return false;
        }

        PsiParameter[] parameters = parameterList.getParameters();
        if (!isType(parameters[0], "android.content.Context")) {
            return false;
        }
        if (count >= 2 && !isType(parameters[1], "android.util.AttributeSet")) {
            return false;
        }
        if (count == 3 && !isType(parameters[2], "int")) {
            return false;
        }
        return true;
    }

    private static boolean isType(PsiParameter parameter, String expectedType) {
        String canonicalText = parameter.getType().getCanonicalText();
        if (canonicalText.endsWith("?")) {
            canonicalText = canonicalText.substring(0, canonicalText.length() - 1);
        }
        return expectedType.equals(canonicalText);
    }
}