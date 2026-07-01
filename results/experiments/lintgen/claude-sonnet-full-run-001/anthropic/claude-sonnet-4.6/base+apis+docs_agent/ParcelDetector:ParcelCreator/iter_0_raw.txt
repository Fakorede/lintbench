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
        // Skip abstract classes — they don't need to provide CREATOR themselves
        if (declaration.isInterface()) {
            return;
        }

        // Check if the class is abstract
        if (declaration.hasModifierProperty("abstract")) {
            return;
        }

        // Anonymous classes can't be parceled in a meaningful way
        if (declaration.getName() == null || declaration.getName().isEmpty()) {
            return;
        }

        // Look for a static CREATOR field in this class (not inherited)
        boolean hasCreator = false;
        for (UField field : declaration.getFields()) {
            if (CREATOR_FIELD.equals(field.getName())) {
                hasCreator = true;
                break;
            }
        }

        if (!hasCreator) {
            // Make sure the class actually directly implements Parcelable (or indirectly,
            // but we still require CREATOR in the concrete class).
            // Report on the class name node if possible.
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class implements `Parcelable` but does not provide a "
                            + "`CREATOR` field");
        }
    }
}