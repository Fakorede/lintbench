/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

/**
 * Looks for custom views that do not define the view constructors needed by
 * layout inflation tools.
 */
public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            " * `View(Context context)`\n" +
            " * `View(Context context, AttributeSet attrs)`\n" +
            " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
            "\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    /** Constructs a new {@link ViewConstructorDetector} */
    public ViewConstructorDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_VIEW);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Abstract classes don't need to have the constructors
        if (declaration.hasModifierProperty("abstract")) {
            return;
        }

        // Check if the class is a direct or indirect subclass of android.view.View
        PsiClass psiClass = declaration.getJavaPsi();

        // Check if any of the required constructors exist
        if (hasValidViewConstructor(psiClass)) {
            return;
        }

        // Report the issue on the class declaration
        String message = String.format(
                "Custom view `%1$s` is missing constructor used by tools: "
                        + "`(Context)` or `(Context, AttributeSet)` "
                        + "or `(Context, AttributeSet, int)`",
                declaration.getName());

        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    /**
     * Checks whether the given class has at least one of the valid view constructors:
     * - View(Context)
     * - View(Context, AttributeSet)
     * - View(Context, AttributeSet, int)
     */
    private static boolean hasValidViewConstructor(@NonNull PsiClass psiClass) {
        PsiMethod[] constructors = psiClass.getConstructors();

        // If there are no constructors defined, the default constructor exists
        // but it doesn't match any view constructor pattern
        if (constructors.length == 0) {
            return false;
        }

        for (PsiMethod constructor : constructors) {
            if (isValidViewConstructor(constructor)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the given constructor matches one of the valid view constructor signatures.
     */
    private static boolean isValidViewConstructor(@NonNull PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0 || parameters.length > 3) {
            return false;
        }

        // First parameter must be Context
        if (!isOfType(parameters[0], CONTEXT_CLASS)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context)
            return true;
        }

        // Second parameter must be AttributeSet
        if (!isOfType(parameters[1], ATTRIBUTE_SET_CLASS)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context, AttributeSet)
            return true;
        }

        // Third parameter must be int
        PsiType thirdParamType = parameters[2].getType();
        if (parameters.length == 3 && thirdParamType.equals(PsiType.INT)) {
            // View(Context, AttributeSet, int)
            return true;
        }

        return false;
    }

    /**
     * Returns true if the given parameter is of the specified fully-qualified type name,
     * or a subtype of it.
     */
    private static boolean isOfType(@NonNull PsiParameter parameter, @NonNull String typeName) {
        PsiType type = parameter.getType();
        String canonicalText = type.getCanonicalText();

        // Direct match
        if (canonicalText.equals(typeName)) {
            return true;
        }

        // Check supertypes
        for (PsiType superType : type.getSuperTypes()) {
            if (superType.getCanonicalText().equals(typeName)) {
                return true;
            }
        }

        return false;
    }
}