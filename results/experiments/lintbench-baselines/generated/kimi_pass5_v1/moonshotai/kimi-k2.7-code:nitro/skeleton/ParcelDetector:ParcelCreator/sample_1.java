package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "Classes implementing the `Parcelable` interface must also declare a static field named `CREATOR` of type `android.os.Parcelable.Creator`.",
                    Category.CORRECTNESS,
                    3,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()
                || declaration.isEnum()
                || declaration.isAnnotationType()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        for (UField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)
                    && isCreatorField(context, field)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements Parcelable but does not declare the required static CREATOR field of type Parcelable.Creator");
    }

    private static boolean isCreatorField(@NonNull JavaContext context, @NonNull UField field) {
        PsiType type = field.getType();
        if (!(type instanceof PsiClassType)) {
            return false;
        }

        PsiClass resolved = ((PsiClassType) type).resolve();
        if (resolved == null) {
            return false;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        return evaluator.implementsInterface(
                resolved, "android.os.Parcelable.Creator", false);
    }
}