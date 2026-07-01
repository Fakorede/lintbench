package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ParcelDetector extends Detector implements Detector.UastScanner {

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
    private static final String PARCELABLE_CREATOR_CLASS = "android.os.Parcelable.Creator";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes - they don't need to provide CREATOR themselves
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip interfaces
        if (psiClass.isInterface()) {
            return;
        }

        // Check if the class directly implements Parcelable or inherits from a class
        // that already has a CREATOR field. If a parent class already defines CREATOR,
        // we don't need to flag this class.
        if (hasCreatorInHierarchy(psiClass)) {
            return;
        }

        // Report the missing CREATOR field
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }

    /**
     * Checks whether the given class or any of its superclasses (up to but not including
     * android.os.Parcelable itself) defines a static CREATOR field of type Parcelable.Creator.
     */
    private boolean hasCreatorInHierarchy(@NonNull PsiClass psiClass) {
        PsiClass current = psiClass;
        while (current != null) {
            String qualifiedName = current.getQualifiedName();
            // Stop if we've reached the Parcelable interface itself
            if (PARCELABLE_CLASS.equals(qualifiedName)) {
                break;
            }

            if (hasCreatorField(current)) {
                return true;
            }

            current = current.getSuperClass();
        }
        return false;
    }

    /**
     * Checks whether the given class directly declares a static field named CREATOR
     * whose type is compatible with Parcelable.Creator.
     */
    private boolean hasCreatorField(@NonNull PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                // Verify the type is Parcelable.Creator (or a subtype)
                PsiType fieldType = field.getType();
                String canonicalText = fieldType.getCanonicalText();
                // Check that the type contains "Parcelable.Creator" or "android.os.Parcelable.Creator"
                if (canonicalText.contains("Parcelable.Creator")
                        || canonicalText.contains("android.os.Parcelable$Creator")) {
                    return true;
                }
                // Also accept if the raw type name is just "CREATOR" of any Creator type
                // by checking the erased type name loosely
                if (canonicalText.startsWith(PARCELABLE_CREATOR_CLASS)
                        || canonicalText.contains("Creator")) {
                    return true;
                }
            }
        }
        return false;
    }
}