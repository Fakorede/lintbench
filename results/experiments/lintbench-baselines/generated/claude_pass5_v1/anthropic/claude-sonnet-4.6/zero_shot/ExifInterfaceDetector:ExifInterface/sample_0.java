/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;

import java.util.Collections;
import java.util.List;

/**
 * Detector that flags usages of {@code android.media.ExifInterface} and suggests
 * using the support library version instead.
 */
public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_EXIF_INTERFACE = "android.media.ExifInterface";

    public static final Issue ISSUE = Issue.create(
            "ExifInterface",
            "Using `android.media.ExifInterface`",
            "The `android.media.ExifInterface` implementation has some known " +
            " security bugs in older versions of Android. There is a new " +
            " implementation available of this library in the support " +
            " library, which is preferable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ExifInterfaceDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    /** Constructs a new {@link ExifInterfaceDetector}. */
    public ExifInterfaceDetector() {
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UImportStatement.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitImportStatement(@NonNull UImportStatement node) {
                String importReference = node.getImportReference();
                if (importReference != null &&
                        importReference.equals(ANDROID_EXIF_INTERFACE)) {
                    PsiElement sourcePsi = node.getSourcePsi();
                    if (sourcePsi != null) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Use `android.support.media.ExifInterface` from the " +
                                "support library instead of `android.media.ExifInterface`");
                    }
                }
            }
        };
    }
}