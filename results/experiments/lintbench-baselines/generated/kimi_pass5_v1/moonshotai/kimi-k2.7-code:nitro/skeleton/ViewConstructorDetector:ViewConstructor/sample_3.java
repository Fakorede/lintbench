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
import com.intellij.psi.PsiType;
import java.util.Arrays;
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
                    "Custom views that are referenced from XML layouts must provide a public "
                            + "constructor with one of the following signatures so that layout "
                            + "inflation tools can instantiate them:\n"
                            + "  View(Context context)\n"
                            + "  View(Context context, AttributeSet attrs)\n"
                            + "  View(Context context, AttributeSet attrs, int defStyle)",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if ("android.view.View".equals(qualifiedName)) {
            return;
        }

        if (declaration.getContainingClass() != null
                && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (!method.isConstructor() || !method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            List<UParameter> params = method.getUastParameters();
            if (params.size() == 1 && isContext(params.get(0).getType())) {
                hasValidConstructor = true;
                break;
            }
            if (params.size() == 2
                    && isContext(params.get(0).getType())
                    && isAttributeSet(params.get(1).getType())) {
                hasValidConstructor = true;
                break;
            }
            if (params.size() == 3
                    && isContext(params.get(0).getType())
                    && isAttributeSet(params.get(1).getType())
                    && isInt(params.get(2).getType())) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            String name = declaration.getName();
            String message =
                    "Custom view "
                            + (name != null ? name : "(anonymous)")
                            + " is missing a constructor with one of the following signatures: "
                            + "View(Context), View(Context, AttributeSet), or "
                            + "View(Context, AttributeSet, int)";
            context.report(ISSUE, declaration, context.getLocation(declaration), message);
        }
    }

    private static boolean isContext(PsiType type) {
        return "android.content.Context".equals(type.getCanonicalText());
    }

    private static boolean isAttributeSet(PsiType type) {
        return "android.util.AttributeSet".equals(type.getCanonicalText());
    }

    private static boolean isInt(PsiType type) {
        return "int".equals(type.getCanonicalText());
    }
}