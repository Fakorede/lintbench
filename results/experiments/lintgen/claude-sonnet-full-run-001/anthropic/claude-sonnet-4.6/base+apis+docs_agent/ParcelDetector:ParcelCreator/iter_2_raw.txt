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
 * Checks that classes implementing Parcelable also provide a CREATOR field.
 */
public class ParcelDetector extends Detector implements SourceCodeScanner {

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
    private static final String PARCELIZE_ANNOTATION = "kotlinx.parcelize.Parcelize";
    private static final String PARCELIZE_ANNOTATION_OLD = "kotlinx.android.parcel.Parcelize";
    private static final String JVM_FIELD_ANNOTATION = "kotlin.jvm.JvmField";

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
        // Skip interfaces
        if (declaration.isInterface()) {
            return;
        }

        // Skip abstract classes
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Anonymous classes can't be parceled in a meaningful way
        String name = declaration.getName();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip classes annotated with @Parcelize (they auto-generate CREATOR)
        for (UAnnotation annotation : declaration.getUAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (PARCELIZE_ANNOTATION.equals(qualifiedName) ||
                    PARCELIZE_ANNOTATION_OLD.equals(qualifiedName)) {
                return;
            }
        }

        // Look for a static CREATOR field in this class (not inherited)
        // Also check for a companion object named CREATOR (Kotlin pattern)
        UField creatorField = null;
        for (UField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                creatorField = field;
                break;
            }
        }

        // Also check inner classes for a companion object named CREATOR
        // In Kotlin, companion object CREATOR : Parcelable.Creator<T> is a valid pattern
        boolean hasCreatorCompanion = false;
        for (UClass innerClass : declaration.getInnerClasses()) {
            String innerName = innerClass.getName();
            if (CREATOR_FIELD.equals(innerName)) {
                hasCreatorCompanion = true;
                break;
            }
        }

        if (creatorField == null && !hasCreatorCompanion) {
            // No CREATOR field found at all
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a "
                            + "`CREATOR` field");
        } else if (creatorField != null && isKotlinFile(context)) {
            // In Kotlin, a val/var field without @JvmField won't be a proper static field
            // accessible from Java as required by Parcelable
            if (!hasJvmFieldAnnotation(creatorField)) {
                context.report(
                        ISSUE,
                        creatorField,
                        context.getNameLocation(creatorField),
                        "Field should be annotated with `@JvmField`");
            }
        }
    }

    private static boolean isKotlinFile(@NonNull JavaContext context) {
        return context.file.getName().endsWith(".kt");
    }

    private static boolean hasJvmFieldAnnotation(@NonNull UField field) {
        for (UAnnotation annotation : field.getUAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (JVM_FIELD_ANNOTATION.equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }
}