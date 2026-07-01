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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;

public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_EXIF_INTERFACE = "android.media.ExifInterface";

    public static final Issue ISSUE =
            Issue.create(
                    "ExifInterface",
                    "Using `android.media.ExifInterface`",
                    "The `android.media.ExifInterface` implementation has some known "
                            + "security bugs in older versions of Android. There is a new "
                            + "implementation available of this library in the support "
                            + "library, which is preferable.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE));

    public ExifInterfaceDetector() {}

    @Override
    public List<String> applicableConstructorTypes() {
        return Collections.singletonList(ANDROID_EXIF_INTERFACE);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod constructor) {
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Use `android.support.media.ExifInterface` from the support library "
                        + "instead of `android.media.ExifInterface`");
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ExifInterface");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement resolved) {
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            if (field.getContainingClass() != null
                    && ANDROID_EXIF_INTERFACE.equals(
                            field.getContainingClass().getQualifiedName())) {
                context.report(
                        ISSUE,
                        reference,
                        context.getLocation(reference),
                        "Use `android.support.media.ExifInterface` from the support library "
                                + "instead of `android.media.ExifInterface`");
            }
        }
    }

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
                if (importReference != null
                        && importReference.equals(ANDROID_EXIF_INTERFACE)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Use `android.support.media.ExifInterface` from the support library "
                                    + "instead of `android.media.ExifInterface`");
                }
            }
        };
    }
}