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
import com.intellij.psi.PsiModifierList;
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
        // Skip abstract classes and interfaces — they don't need a CREATOR field.
        if (declaration.isInterface()) {
            return;
        }

        PsiModifierList classModifiers = declaration.getModifierList();
        if (classModifiers != null && classModifiers.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Look for a static field named CREATOR in the class itself (not inherited).
        PsiField[] fields = declaration.getFields();
        for (PsiField field : fields) {
            if ("CREATOR".equals(field.getName())) {
                PsiModifierList modifiers = field.getModifierList();
                if (modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC)) {
                    // Found the required CREATOR static field.
                    return;
                }
            }
        }

        // No CREATOR field found — report the issue on the class name node.
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }
}