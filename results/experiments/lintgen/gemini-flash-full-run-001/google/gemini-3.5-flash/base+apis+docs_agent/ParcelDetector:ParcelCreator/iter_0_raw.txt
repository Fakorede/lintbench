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
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing "
                    + "the Parcelable interface must also have a static field called `CREATOR`, "
                    + "which is an object implementing the `Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.os.Parcelable");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Only check concrete classes
        if (declaration.isInterface()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)
                || declaration.getName() == null) {
            return;
        }

        // Kotlin @Parcelize generates the CREATOR field automatically
        if (declaration.hasAnnotation("kotlinx.parcelize.Parcelize")
                || declaration.hasAnnotation("kotlinx.android.parcel.Parcelize")) {
            return;
        }

        // Check if the class itself has a CREATOR field
        boolean hasCreator = false;
        for (PsiField field : declaration.getFields()) {
            if ("CREATOR".equals(field.getName())) {
                hasCreator = true;
                break;
            }
        }

        // In Kotlin, the CREATOR field might be inside a companion object
        if (!hasCreator) {
            for (PsiClass innerClass : declaration.getInnerClasses()) {
                if (innerClass.getName() != null && innerClass.getName().endsWith("Companion")) {
                    for (PsiField field : innerClass.getFields()) {
                        if ("CREATOR".equals(field.getName())) {
                            hasCreator = true;
                            break;
                        }
                    }
                }
                if (hasCreator) {
                    break;
                }
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but is missing a `CREATOR` field"
            );
        }
    }
}