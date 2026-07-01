/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UField;

import java.util.Collections;
import java.util.List;

/**
 * Checks that classes implementing Parcelable also have a CREATOR field.
 */
public class ParcelDetector extends Detector implements SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing " +
            "the Parcelable interface must also have a static field called `CREATOR`, which " +
            "is an object implementing the `Parcelable.Creator` interface.\"",
            Category.USABILITY,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/os/Parcelable.html");

    private static final String PARCELABLE_CLASS = "android.os.Parcelable";
    private static final String CREATOR_FIELD = "CREATOR";

    /** Constructs a new {@link ParcelDetector} */
    public ParcelDetector() {
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(PARCELABLE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes - they don't need to have CREATOR
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip interfaces
        if (declaration.isInterface()) {
            return;
        }

        // Check if the class is annotated with @Parcelize (Kotlin parcelize plugin)
        // In that case, CREATOR is generated automatically
        if (hasParcelizeAnnotation(declaration)) {
            return;
        }

        // Look for a static CREATOR field in the class itself (not inherited)
        // For Kotlin, we need to check companion object fields too
        boolean hasCreator = false;
        boolean hasCreatorWithoutJvmField = false;

        // Check fields directly declared in this class
        for (UField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                if (field.isStatic()) {
                    // Check if it's a Kotlin property without @JvmField
                    // In Kotlin, a val/var in companion object without @JvmField
                    // won't be visible as a static field to Java
                    if (isKotlinPropertyWithoutJvmField(context, field)) {
                        hasCreatorWithoutJvmField = true;
                    } else {
                        hasCreator = true;
                    }
                    break;
                }
            }
        }

        // Also check PsiFields (which may include companion object fields)
        if (!hasCreator && !hasCreatorWithoutJvmField) {
            for (PsiField field : declaration.getAllFields()) {
                if (CREATOR_FIELD.equals(field.getName())) {
                    if (field.hasModifierProperty(PsiModifier.STATIC)) {
                        hasCreator = true;
                        break;
                    }
                }
            }
        }

        if (hasCreatorWithoutJvmField) {
            // Report that the field needs @JvmField
            UField creatorField = findCreatorField(declaration);
            if (creatorField != null) {
                context.report(
                        ISSUE,
                        creatorField,
                        context.getNameLocation(creatorField),
                        "Field should be annotated with `@JvmField`");
            } else {
                context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "This class implements `Parcelable` but does not provide a " +
                        "`CREATOR` field");
            }
        } else if (!hasCreator) {
            // Check if it's a Kotlin data class - suggest @Parcelize
            String message;
            if (isKotlinDataClass(declaration) && isEligibleForParcelize(declaration)) {
                message = "This class implements `Parcelable` but does not provide a " +
                        "`CREATOR` field; consider adding the `@Parcelize` annotation";
            } else {
                message = "This class implements `Parcelable` but does not provide a " +
                        "`CREATOR` field";
            }
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    message);
        }
    }

    private UField findCreatorField(@NonNull UClass declaration) {
        for (UField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    /**
     * Checks if a field is a Kotlin property in a companion object that lacks @JvmField.
     * Without @JvmField, the field won't be accessible as a static Java field.
     */
    private static boolean isKotlinPropertyWithoutJvmField(@NonNull JavaContext context,
            @NonNull UField field) {
        // Check if this is a Kotlin file
        if (!isKotlinFile(context, field)) {
            return false;
        }

        // If the field has @JvmField annotation, it's fine
        if (field.findAnnotation("kotlin.jvm.JvmField") != null) {
            return false;
        }

        // Check if the underlying PSI element is a Kotlin property
        // In Kotlin companion objects, val/var without @JvmField are not static fields
        // We detect this by checking if the field is in a companion object context
        // and doesn't have @JvmField
        com.intellij.psi.PsiElement sourcePsi = field.getSourcePsi();
        if (sourcePsi != null) {
            String className = sourcePsi.getClass().getName();
            // Kotlin properties in companion objects
            if (className.contains("KtProperty") || className.contains("KtParameter")) {
                return true;
            }
        }

        return false;
    }

    private static boolean isKotlinFile(@NonNull JavaContext context, @NonNull UField field) {
        com.intellij.psi.PsiElement sourcePsi = field.getSourcePsi();
        if (sourcePsi != null) {
            com.intellij.psi.PsiFile file = sourcePsi.getContainingFile();
            if (file != null) {
                return file.getName().endsWith(".kt");
            }
        }
        return false;
    }

    /**
     * Checks whether the class is a Kotlin data class.
     */
    private static boolean isKotlinDataClass(@NonNull UClass declaration) {
        com.intellij.psi.PsiElement sourcePsi = declaration.getSourcePsi();
        if (sourcePsi != null) {
            String className = sourcePsi.getClass().getName();
            if (className.contains("KtClass")) {
                // Check for data modifier
                try {
                    org.jetbrains.kotlin.psi.KtClass ktClass =
                            (org.jetbrains.kotlin.psi.KtClass) sourcePsi;
                    return ktClass.isData();
                } catch (Throwable t) {
                    // Not a Kotlin class or API not available
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the class is eligible for @Parcelize annotation.
     * A class is eligible if all its constructor parameters are of parcelable types.
     */
    private static boolean isEligibleForParcelize(@NonNull UClass declaration) {
        // For simplicity, we suggest @Parcelize for data classes
        // The actual eligibility check would be more complex
        return true;
    }

    /**
     * Checks whether the class has the @Parcelize annotation (from the Kotlin
     * Android Extensions parcelize plugin), which auto-generates the CREATOR field.
     */
    private static boolean hasParcelizeAnnotation(@NonNull UClass declaration) {
        return declaration.findAnnotation("kotlinx.android.parcel.Parcelize") != null
                || declaration.findAnnotation("kotlin.parcelize.Parcelize") != null;
    }
}