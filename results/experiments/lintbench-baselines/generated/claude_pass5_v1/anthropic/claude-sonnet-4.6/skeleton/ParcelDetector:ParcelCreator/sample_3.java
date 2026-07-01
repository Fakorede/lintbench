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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
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

    private static final String PARCELABLE_CLASS = "android.os.Parcelable";
    private static final String CREATOR_FIELD = "CREATOR";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes and interfaces — they don't need a CREATOR field
        if (declaration.isInterface()) {
            return;
        }

        PsiClass psiClass = declaration.getJavaPsi();

        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Check whether this class directly implements Parcelable or inherits it from a
        // superclass.  If it inherits from a concrete superclass that already provides a
        // CREATOR field we don't need to flag it, but if the superclass is abstract (or
        // the class re-declares Parcelable itself) we do.
        //
        // The simplest correct approach: look for a static field named CREATOR anywhere
        // in the class hierarchy that is owned by a concrete (non-abstract) ancestor.
        // If no such field exists, report the class.

        if (!hasCreatorField(psiClass)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field");
        }
    }

    /**
     * Returns true if the given class or any of its superclasses (up to but not including
     * {@code android.os.Parcelable} itself) declares a static field named {@code CREATOR}.
     */
    private static boolean hasCreatorField(@NonNull PsiClass psiClass) {
        PsiClass current = psiClass;
        while (current != null) {
            String qualifiedName = current.getQualifiedName();
            // Stop walking up when we reach the Parcelable interface itself
            if (PARCELABLE_CLASS.equals(qualifiedName)) {
                break;
            }

            for (PsiField field : current.getFields()) {
                if (CREATOR_FIELD.equals(field.getName())
                        && field.hasModifierProperty(PsiModifier.STATIC)) {
                    return true;
                }
            }

            current = current.getSuperClass();
        }
        return false;
    }
}