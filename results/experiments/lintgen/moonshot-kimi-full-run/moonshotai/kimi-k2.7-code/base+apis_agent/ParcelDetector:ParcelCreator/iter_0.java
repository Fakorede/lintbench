package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final String PARCELABLE = "android.os.Parcelable";
    private static final String CREATOR_TYPE = "android.os.Parcelable.Creator";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || declaration.isEnum()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (!hasCreatorField(declaration)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Missing Parcelable CREATOR field"
            );
        }
    }

    private static boolean hasCreatorField(@NotNull UClass cls) {
        for (UField field : cls.getFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                if (field.getType() instanceof PsiClassType) {
                    PsiClassType classType = (PsiClassType) field.getType();
                    if (classType.resolve() != null
                            && CREATOR_TYPE.equals(classType.resolve().getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "According to the Parcelable documentation, classes implementing Parcelable must "
                    + "declare a static field named CREATOR of type android.os.Parcelable.Creator.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );
}