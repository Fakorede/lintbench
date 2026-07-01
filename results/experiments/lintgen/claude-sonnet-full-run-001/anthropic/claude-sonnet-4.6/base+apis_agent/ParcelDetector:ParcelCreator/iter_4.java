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

import org.jetbrains.uast.UAnnotation;
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

        // Check if CREATOR field exists as a proper static field in the class itself
        for (PsiField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
        }

        // Check companion objects for Kotlin classes - look for CREATOR field with @JvmField
        for (PsiClass innerClass : declaration.getInnerClasses()) {
            if (isCompanionObject(innerClass)) {
                for (PsiField field : innerClass.getFields()) {
                    if (CREATOR_FIELD.equals(field.getName())) {
                        // Found CREATOR in companion object - check if it has @JvmField
                        if (!hasJvmFieldAnnotation(field)) {
                            // Report error: CREATOR in companion object needs @JvmField
                            UField uField = findUField(declaration, field);
                            if (uField != null) {
                                context.report(
                                        ISSUE,
                                        uField,
                                        context.getNameLocation(uField),
                                        "Field should be annotated with `@JvmField`"
                                );
                            } else {
                                context.report(
                                        ISSUE,
                                        declaration,
                                        context.getNameLocation(declaration),
                                        "Field should be annotated with `@JvmField`"
                                );
                            }
                        }
                        return;
                    }
                }
            }
        }

        // Also check inherited fields from getAllFields (but not companion objects already checked)
        for (PsiField field : declaration.getAllFields()) {
            if (CREATOR_FIELD.equals(field.getName())
                    && field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class implements `Parcelable` but does not provide a `CREATOR` field"
        );
    }

    private UField findUField(UClass declaration, PsiField field) {
        for (UField uField : declaration.getFields()) {
            if (uField.getPsi().equals(field)) {
                return uField;
            }
        }
        // Check in inner classes
        for (UClass innerClass : declaration.getInnerClasses()) {
            for (UField uField : innerClass.getFields()) {
                if (uField.getPsi().equals(field)) {
                    return uField;
                }
            }
        }
        return null;
    }

    private boolean isCompanionObject(PsiClass innerClass) {
        String name = innerClass.getName();
        return "Companion".equals(name);
    }

    private boolean hasJvmFieldAnnotation(PsiField field) {
        for (PsiAnnotation annotation : field.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (JVM_FIELD_ANNOTATION.equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParcelizeAnnotation(UClass declaration) {
        for (UAnnotation annotation : declaration.getUAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (PARCELIZE_ANNOTATION.equals(qualifiedName)
                    || PARCELIZE_ANNOTATION_OLD.equals(qualifiedName)) {
                return true;
            }
        }
        // Also check PSI annotations
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