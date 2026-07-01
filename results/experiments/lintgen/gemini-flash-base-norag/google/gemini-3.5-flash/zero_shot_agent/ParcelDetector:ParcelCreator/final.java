package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "Classes implementing the `Parcelable` interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (declaration.getName() == null) {
            return;
        }

        if (declaration.findAnnotation("kotlinx.parcelize.Parcelize") != null ||
                declaration.findAnnotation("kotlinx.android.parcel.Parcelize") != null) {
            return;
        }

        boolean hasCreator = false;

        PsiField creatorField = declaration.findFieldByName("CREATOR", false);
        if (creatorField != null) {
            boolean isPublic = creatorField.hasModifierProperty(PsiModifier.PUBLIC);
            boolean isStatic = creatorField.hasModifierProperty(PsiModifier.STATIC);
            if (!isPublic || !isStatic) {
                boolean isKotlin = context.getPsiFile() != null && context.getPsiFile().getName().endsWith(".kt");
                if (isKotlin) {
                    context.report(
                            ISSUE,
                            creatorField,
                            context.getNameLocation(creatorField),
                            "Field should be annotated with @JvmField"
                    );
                } else {
                    context.report(
                            ISSUE,
                            creatorField,
                            context.getNameLocation(creatorField),
                            "The CREATOR field must be public static"
                    );
                }
                return;
            }
            hasCreator = true;
        }

        if (!hasCreator) {
            for (PsiClass inner : declaration.getInnerClasses()) {
                PsiField companionField = inner.findFieldByName("CREATOR", false);
                if (companionField != null) {
                    boolean isKotlin = context.getPsiFile() != null && context.getPsiFile().getName().endsWith(".kt");
                    if (isKotlin) {
                        context.report(
                                ISSUE,
                                companionField,
                                context.getNameLocation(companionField),
                                "Field should be annotated with @JvmField"
                        );
                    } else {
                        context.report(
                                ISSUE,
                                companionField,
                                context.getNameLocation(companionField),
                                "The CREATOR field must be in the outer class"
                        );
                    }
                    return;
                }
            }
        }

        if (!hasCreator) {
            for (PsiClass inner : declaration.getInnerClasses()) {
                if ("CREATOR".equals(inner.getName())) {
                    if (context.getEvaluator().implementsInterface(inner, "android.os.Parcelable.Creator", false)) {
                        hasCreator = true;
                        break;
                    }
                }
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements Parcelable but does not provide a CREATOR field"
            );
        }
    }
}