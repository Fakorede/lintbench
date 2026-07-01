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
 * Checks that custom views define the expected constructors needed for XML inflation.
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
        // Abstract classes do not need to provide constructors
        if (declaration.hasModifierProperty("abstract")) {
            return;
        }

        // Anonymous classes cannot define constructors in the normal sense
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        // Check if the class is anonymous
        if (psiClass instanceof com.intellij.psi.PsiAnonymousClass) {
            return;
        }

        PsiMethod[] constructors = psiClass.getConstructors();

        // If there are no constructors defined, the default constructor is used,
        // which doesn't match any of the required signatures.
        // However, if the class has no constructors at all, we should still warn
        // unless the superclass provides the needed constructors (but since we're
        // checking custom views, we should warn).
        if (constructors.length == 0) {
            // No explicit constructors - the default no-arg constructor is used,
            // which is not valid for XML inflation
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor " +
                    "used by tools: `(Context)` or `(Context, AttributeSet)` " +
                    "or `(Context, AttributeSet, int)`");
            return;
        }

        // Check if any of the constructors match the required signatures
        if (!hasValidViewConstructor(constructors)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor " +
                    "used by tools: `(Context)` or `(Context, AttributeSet)` " +
                    "or `(Context, AttributeSet, int)`");
        }
    }

    /**
     * Returns true if any of the given constructors match one of the valid View constructor
     * signatures:
     * <ul>
     *   <li>View(Context)</li>
     *   <li>View(Context, AttributeSet)</li>
     *   <li>View(Context, AttributeSet, int)</li>
     * </ul>
     */
    private static boolean hasValidViewConstructor(@NonNull PsiMethod[] constructors) {
        for (PsiMethod constructor : constructors) {
            if (isValidViewConstructor(constructor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given constructor matches one of the valid View constructor signatures.
     */
    private static boolean isValidViewConstructor(@NonNull PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0 || parameters.length > 3) {
            return false;
        }

        // First parameter must be Context
        if (!isContextType(parameters[0].getType())) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context)
            return true;
        }

        // Second parameter must be AttributeSet
        if (!isAttributeSetType(parameters[1].getType())) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context, AttributeSet)
            return true;
        }

        // Third parameter must be int
        if (parameters.length == 3) {
            PsiType thirdType = parameters[2].getType();
            if (PsiType.INT.equals(thirdType)) {
                // View(Context, AttributeSet, int)
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the given type is or extends android.content.Context.
     */
    private static boolean isContextType(@NonNull PsiType type) {
        String canonicalText = type.getCanonicalText();
        return CONTEXT_CLASS.equals(canonicalText) ||
               canonicalText.endsWith(".Context");
    }

    /**
     * Returns true if the given type is android.util.AttributeSet.
     */
    private static boolean isAttributeSetType(@NonNull PsiType type) {
        String canonicalText = type.getCanonicalText();
        return ATTRIBUTE_SET_CLASS.equals(canonicalText) ||
               canonicalText.endsWith(".AttributeSet");
    }
}