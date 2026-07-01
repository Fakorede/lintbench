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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

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
        // Also check companion objects (for Kotlin)
        PsiField creatorField = findCreatorFieldInClass(declaration);

        if (creatorField == null) {
            // Check companion objects for Kotlin classes
            UField companionCreatorField = findCreatorInCompanionObject(declaration);

            if (companionCreatorField == null) {
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
                // Found CREATOR in companion object - check if it has @JvmField
                if (!hasJvmFieldAnnotation(companionCreatorField)) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getLocation(companionCreatorField),
                            "Field should be annotated with `@JvmField`"
                    );
                }
            }
        } else {
            // CREATOR field exists directly in the class - check if it's static
            if (!creatorField.hasModifierProperty(PsiModifier.STATIC)) {
                // Check if it has @JvmField annotation
                if (!hasJvmFieldAnnotationOnPsi(creatorField)) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getLocation(creatorField),
                            "Field should be annotated with `@JvmField`"
                    );
                }
            }
        }
    }

    private PsiField findCreatorFieldInClass(UClass declaration) {
        for (PsiField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private UField findCreatorInCompanionObject(UClass declaration) {
        // In Kotlin, companion objects are inner classes named "Companion"
        for (PsiClass innerClass : declaration.getInnerClasses()) {
            // Check if this is a companion object
            if (isCompanionObject(innerClass)) {
                // Look for CREATOR field in the companion object
                for (PsiField field : innerClass.getFields()) {
                    if (CREATOR_FIELD.equals(field.getName())) {
                        // Try to get the UField
                        if (field instanceof UField) {
                            return (UField) field;
                        }
                        // Try to find it via UAST
                        org.jetbrains.uast.UElement uElement =
                                org.jetbrains.uast.UastContextKt.toUElement(field);
                        if (uElement instanceof UField) {
                            return (UField) uElement;
                        }
                        // Return a wrapper - we need to report on the field
                        // We'll handle this differently
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private boolean isCompanionObject(PsiClass innerClass) {
        // Kotlin companion objects have the annotation @kotlin.jvm.JvmStatic or
        // are named "Companion" and have specific Kotlin metadata
        String name = innerClass.getName();
        if ("Companion".equals(name)) {
            return true;
        }
        // Check for @CompanionObject or similar markers
        for (PsiAnnotation annotation : innerClass.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if ("kotlin.jvm.JvmStatic".equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasJvmFieldAnnotation(UField field) {
        for (UAnnotation annotation : field.getUAnnotations()) {
            if (JVM_FIELD_ANNOTATION.equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasJvmFieldAnnotationOnPsi(PsiField field) {
        for (PsiAnnotation annotation : field.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (JVM_FIELD_ANNOTATION.equals(qualifiedName)) {
                return true;
            }
        }
        return false;
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