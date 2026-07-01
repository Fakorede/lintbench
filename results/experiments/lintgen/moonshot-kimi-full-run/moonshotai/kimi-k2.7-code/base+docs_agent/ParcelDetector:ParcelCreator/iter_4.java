package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.UastScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class ParcelDetector extends Detector implements UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing `Parcelable` `CREATOR` field",
            "According to the `Parcelable` documentation, every concrete class that "
                    + "implements `android.os.Parcelable` must declare a static field "
                    + "named `CREATOR` of type `android.os.Parcelable.Creator`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
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
                        || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(
                        psiClass, "android.os.Parcelable", true)) {
                    return;
                }

                for (PsiField field : psiClass.getFields()) {
                    if (!"CREATOR".equals(field.getName())
                            || !field.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }

                    PsiClass typeClass = context.getEvaluator().getTypeClass(field.getType());
                    if (typeClass != null
                            && "android.os.Parcelable.Creator".equals(
                                    typeClass.getQualifiedName())) {
                        return;
                    }
                }

                context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This class implements `Parcelable` but does not define a static `CREATOR` field");
            }
        };
    }
}