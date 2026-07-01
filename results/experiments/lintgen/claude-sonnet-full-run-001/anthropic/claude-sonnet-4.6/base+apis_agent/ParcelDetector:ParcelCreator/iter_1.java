package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiAnnotation;
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
    private static final String PARCELIZE_ANNOTATION = "kotlinx.parcelize.Parcelize";
    private static final String PARCELIZE_ANNOTATION_OLD = "kotlinx.android.parcel.Parcelize";
    private static final String JVM_FIELD_ANNOTATION = "kotlin.jvm.JvmField";

    public ParcelDetector() {
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Skip interfaces
        if (declaration.isInterface()) {
            return;
        }

        // Skip abstract classes
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip classes annotated with @Parcelize
        if (hasParcelizeAnnotation(declaration)) {
            return;
        }

        // Look for a static field named CREATOR declared directly in this class
        PsiField creatorField = null;
        for (PsiField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                creatorField = field;
                break;
            }
        }

        if (creatorField == null) {
            // No CREATOR field found in this class directly
            // Check if a parent class already provides it (inherited)
            boolean inheritedCreator = false;
            for (PsiField field : declaration.getAllFields()) {
                if (CREATOR_FIELD.equals(field.getName())
                        && field.hasModifierProperty(PsiModifier.STATIC)) {
                    inheritedCreator = true;
                    break;
                }
            }

            if (!inheritedCreator) {
                context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "This class implements `Parcelable` but does not provide a `CREATOR` field"
                );
            }
        } else {
            // CREATOR field exists - check if it's properly annotated with @JvmField in Kotlin
            if (!creatorField.hasModifierProperty(PsiModifier.STATIC)) {
                // In Kotlin, a field in a companion object without @JvmField won't be static
                // Check if it has @JvmField annotation
                boolean hasJvmField = false;
                for (PsiAnnotation annotation : creatorField.getAnnotations()) {
                    String qualifiedName = annotation.getQualifiedName();
                    if (JVM_FIELD_ANNOTATION.equals(qualifiedName)) {
                        hasJvmField = true;
                        break;
                    }
                }
                if (!hasJvmField) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getNameLocation(creatorField),
                            "Field should be annotated with `@JvmField`"
                    );
                }
            }
        }
    }

    private boolean hasParcelizeAnnotation(UClass declaration) {
        for (PsiAnnotation annotation : declaration.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (PARCELIZE_ANNOTATION.equals(qualifiedName)
                    || PARCELIZE_ANNOTATION_OLD.equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }
}