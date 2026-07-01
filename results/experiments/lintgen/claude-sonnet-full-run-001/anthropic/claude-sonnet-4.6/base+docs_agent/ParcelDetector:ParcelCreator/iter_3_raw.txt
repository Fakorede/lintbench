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

import org.jetbrains.uast.UAnnotation;
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

        // Look for a static CREATOR field
        boolean hasCreator = false;
        boolean hasCreatorWithoutJvmField = false;

        // Check fields directly declared in this class via UAST
        for (UField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                if (field.isStatic()) {
                    hasCreator = true;
                    break;
                }
            }
        }

        // Also check via PSI (which may include companion object fields in Kotlin)
        if (!hasCreator) {
            for (PsiField field : declaration.getAllFields()) {
                if (CREATOR_FIELD.equals(field.getName())
                        && field.hasModifierProperty(PsiModifier.STATIC)) {
                    hasCreator = true;
                    break;
                }
            }
        }

        // Check for Kotlin companion object with CREATOR field missing @JvmField
        if (!hasCreator) {
            // Look for inner classes that are companion objects
            for (UClass innerClass : declaration.getInnerClasses()) {
                if (isCompanionObject(innerClass)) {
                    for (UField field : innerClass.getFields()) {
                        if (CREATOR_FIELD.equals(field.getName())) {
                            // Found CREATOR in companion object but without @JvmField
                            // it won't be visible as a static field
                            hasCreatorWithoutJvmField = true;
                            // Report the missing @JvmField annotation
                            context.report(
                                    ISSUE,
                                    field,
                                    context.getNameLocation(field),
                                    "Field should be annotated with `@JvmField`");
                            return;
                        }
                    }
                }
            }
        }

        if (!hasCreator && !hasCreatorWithoutJvmField) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a `CREATOR` field");
        }
    }

    /**
     * Checks whether the class has the @Parcelize annotation (from the Kotlin
     * Android Extensions parcelize plugin), which auto-generates the CREATOR field.
     */
    private static boolean hasParcelizeAnnotation(@NonNull UClass declaration) {
        return declaration.findAnnotation("kotlinx.android.parcel.Parcelize") != null
                || declaration.findAnnotation("kotlin.parcelize.Parcelize") != null;
    }

    /**
     * Checks whether the given inner class is a Kotlin companion object.
     */
    private static boolean isCompanionObject(@NonNull UClass innerClass) {
        // Kotlin companion objects have the name "Companion" by default
        // and are annotated with specific annotations
        String name = innerClass.getName();
        if ("Companion".equals(name)) {
            return true;
        }
        // Check for the companion object annotation
        for (UAnnotation annotation : innerClass.getUAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if ("kotlin.jvm.JvmStatic".equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }
}