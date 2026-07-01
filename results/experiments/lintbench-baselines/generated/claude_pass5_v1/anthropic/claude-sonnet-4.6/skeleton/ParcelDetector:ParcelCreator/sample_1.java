package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ParcelCreator",
                    "Missing Parcelable `CREATOR` field",
                    "According to the `Parcelable` interface documentation, "
                            + "\"Classes implementing the Parcelable interface must also have a "
                            + "static field called `CREATOR`, which is an object implementing the "
                            + "`Parcelable.Creator` interface.\"",
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
        // Skip interfaces and abstract classes — they don't need a CREATOR field themselves.
        if (declaration.isInterface()) {
            return;
        }

        com.intellij.psi.PsiClass psiClass = declaration.getJavaPsi();

        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Check whether the class (not its ancestors) declares a static field named CREATOR.
        boolean hasCreator = false;
        for (PsiField field : psiClass.getFields()) {
            if ("CREATOR".equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                // Verify the field type is (or relates to) Parcelable.Creator
                PsiType type = field.getType();
                String canonicalText = type.getCanonicalText();
                if (canonicalText.contains("Parcelable.Creator")
                        || canonicalText.contains("android.os.Parcelable.Creator")) {
                    hasCreator = true;
                    break;
                }
                // Some implementations use a raw or erased type; accept any static CREATOR field.
                hasCreator = true;
                break;
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a "
                            + "`CREATOR` field");
        }
    }
}