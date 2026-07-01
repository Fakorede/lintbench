package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.visitor.UElementHandler;

public class ViewConstructorDetector extends Detector implements Detector.SourceCodeScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ViewConstructorDetector.class,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a "
                    + "constructor with one of the following signatures: `View(Context context)`, "
                    + "`View(Context context, AttributeSet attrs)`, or "
                    + "`View(Context context, AttributeSet attrs, int defStyle)`.\n\n"
                    + "If your custom view needs to perform initialization which does not apply "
                    + "when used in a layout editor, you can surround that code with a check to "
                    + "see if `View#isInEditMode()` is false, since that method will return `false` "
                    + "at runtime but `true` within a user interface editor.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    @Override
    public List<Class<? extends UElement>> getApplicableNodeTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                PsiClass psiClass = node.getJavaPsi();
                if (psiClass == null
                        || psiClass.isInterface()
                        || psiClass.isEnum()
                        || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(psiClass, "android.view.View", false)) {
                    return;
                }

                boolean hasXmlConstructor = false;
                for (PsiMethod constructor : psiClass.getConstructors()) {
                    if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                        continue;
                    }
                    PsiParameter[] params = constructor.getParameterList().getParameters();
                    if (params.length == 1
                            && isType(params[0], "android.content.Context")) {
                        hasXmlConstructor = true;
                        break;
                    } else if (params.length == 2
                            && isType(params[0], "android.content.Context")
                            && isType(params[1], "android.util.AttributeSet")) {
                        hasXmlConstructor = true;
                        break;
                    } else if (params.length == 3
                            && isType(params[0], "android.content.Context")
                            && isType(params[1], "android.util.AttributeSet")
                            && PsiType.INT.equals(params[2].getType())) {
                        hasXmlConstructor = true;
                        break;
                    }
                }

                if (!hasXmlConstructor) {
                    String name = psiClass.getName();
                    String message = "Custom view `" + name + "` is missing a constructor for XML "
                            + "inflation. Add one of: View(Context), View(Context, AttributeSet), "
                            + "or View(Context, AttributeSet, int)";
                    context.report(ISSUE, psiClass, context.getNameLocation(psiClass), message);
                }
            }
        };
    }

    private static boolean isType(@NotNull PsiParameter parameter, @NotNull String fqName) {
        return fqName.equals(parameter.getType().getCanonicalText());
    }
}