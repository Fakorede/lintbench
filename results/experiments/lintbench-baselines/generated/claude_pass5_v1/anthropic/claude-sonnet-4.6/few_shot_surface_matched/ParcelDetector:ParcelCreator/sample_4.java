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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String PARCELABLE = "android.os.Parcelable";

    public static final Issue ISSUE =
            Issue.create(
                            "ParcelCreator",
                            "Missing Parcelable `CREATOR` field",
                            "According to the `Parcelable` interface documentation, "
                                    + "\"Classes implementing the Parcelable interface must also have a "
                                    + "static field called `CREATOR`, which is an object implementing the "
                                    + "`Parcelable.Creator` interface.\"",
                            Category.CORRECTNESS,
                            7,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ParcelDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip the Parcelable interface itself
        if (PARCELABLE.equals(declaration.getQualifiedName())) {
            return;
        }

        // Skip abstract classes — they don't need to provide CREATOR
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip interfaces
        if (declaration.isInterface()) {
            return;
        }

        // Check whether the class declares a static field named CREATOR
        for (PsiField field : declaration.getAllFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }
}