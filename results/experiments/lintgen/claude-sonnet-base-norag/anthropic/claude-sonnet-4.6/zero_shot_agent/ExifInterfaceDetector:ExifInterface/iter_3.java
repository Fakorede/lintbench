/*
 * Copyright (C) 2016 The Android Open Source Project
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

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

/**
 * Detector that flags usages of android.media.ExifInterface, suggesting the
 * support library version instead.
 */
public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_MEDIA_EXIF_INTERFACE = "android.media.ExifInterface";

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

    /** Constructs a new {@link ExifInterfaceDetector} */
    public ExifInterfaceDetector() {
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UImportStatement.class);
    }

    @Override
    public UastVisitor createUastHandler(@NonNull JavaContext context) {
        return new ExifInterfaceVisitor(context);
    }

    // Use the UElementHandler approach instead
    private static class ExifInterfaceVisitor extends com.android.tools.lint.client.api.UElementHandler {

        private final JavaContext mContext;

        ExifInterfaceVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitImportStatement(@NonNull UImportStatement node) {
            org.jetbrains.uast.UElement importRef = node.getImportReference();
            if (importRef != null) {
                String importReference = importRef.asSourceString();
                if (importReference != null && importReference.equals(ANDROID_MEDIA_EXIF_INTERFACE)) {
                    mContext.report(
                            ISSUE,
                            node,
                            mContext.getLocation(node),
                            "Use `android.support.media.ExifInterface` from the support library " +
                            "instead of `android.media.ExifInterface`");
                }
            }
        }
    }

    // Dummy class reference to avoid compilation error on createUastHandler
    private static class UastVisitor {}
}