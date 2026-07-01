package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements Detector.SourceCodeScanner {
    private static final String PARCELABLE = "android.os.Parcelable";
    private static final String CREATOR = "android.os.Parcelable.Creator";
    private static final String CREATOR_FIELD_NAME = "CREATOR";

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable CREATOR field",
            "Classes implementing the Parcelable interface must also have a static field called CREATOR, which is an object implementing the Parcelable.Creator interface.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @NotNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.INTERFACE)
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        for (UField field : declaration.getFields()) {
            if (CREATOR_FIELD_NAME.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                if (isCreatorType(context, field)) {
                    return;
                }
            }
        }

        String name = declaration.getQualifiedName();
        if (name == null) {
            name = declaration.getName();
        }

        String message = String.format(
                "The class %1$s does not define a CREATOR field. "
                        + "Classes implementing the Parcelable interface must also have a static field called CREATOR, which is an object implementing the Parcelable.Creator interface.",
                name);
        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    private static boolean isCreatorType(@NotNull JavaContext context, @NotNull UField field) {
        if (!(field.getType() instanceof PsiClassType)) {
            return false;
        }
        PsiClassType classType = (PsiClassType) field.getType();
        PsiClass resolved = classType.resolve();
        if (resolved == null) {
            return false;
        }
        return context.getEvaluator().extendsClass(resolved, CREATOR, true);
    }
}