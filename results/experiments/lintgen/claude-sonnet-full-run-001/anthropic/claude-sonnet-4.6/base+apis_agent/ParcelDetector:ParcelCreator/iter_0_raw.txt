package com.android.tools.lint.checks;

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

import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing " +
            "the Parcelable interface must also have a static field called `CREATOR`, which " +
            "is an object implementing the `Parcelable.Creator` interface.\"",
            Category.CORRECTNESS,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/reference/android/os/Parcelable.html");

    private static final String PARCELABLE_CLASS = "android.os.Parcelable";
    private static final String CREATOR_FIELD = "CREATOR";

    public ParcelDetector() {
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Skip abstract classes and interfaces — they don't need CREATOR
        if (declaration.isInterface()) {
            return;
        }

        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Check if the class directly implements Parcelable (not just inherits it)
        // We still want to flag concrete classes that implement Parcelable without CREATOR
        // even if they inherit from another Parcelable class, because each concrete class
        // needs its own CREATOR.

        // Look for a static field named CREATOR
        boolean hasCreator = false;
        for (PsiField field : declaration.getAllFields()) {
            if (CREATOR_FIELD.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                hasCreator = true;
                break;
            }
        }

        if (!hasCreator) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field"
            );
        }
    }
}