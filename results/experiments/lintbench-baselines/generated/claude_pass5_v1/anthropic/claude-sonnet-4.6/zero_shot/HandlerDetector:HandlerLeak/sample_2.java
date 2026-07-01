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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

/**
 * Checks for potential Handler leaks.
 */
public class HandlerDetector extends Detector implements Detector.UastScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this `Handler` is declared as an inner class, it may prevent the outer " +
            "class from being garbage collected. If the Handler is using a `Looper` or " +
            "`MessageQueue` for a thread other than the main thread, then there is no issue. " +
            "If the `Handler` is using the `Looper` or `MessageQueue` of the main thread, " +
            "you need to fix your `Handler` declaration, as follows: Declare the `Handler` " +
            "as a static class; In the outer class, instantiate a `WeakReference` to the " +
            "outer class and pass this object to your `Handler` when you instantiate the " +
            "`Handler`; Make all references to members of the outer class using the " +
            "`WeakReference` object.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(
                    HandlerDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String ANDROID_OS_HANDLER = "android.os.Handler";

    /** Constructs a new {@link HandlerDetector} */
    public HandlerDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_OS_HANDLER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this class is an inner class (non-static) or anonymous class
        // that extends Handler.

        // Get the PsiClass to check modifiers
        PsiClass psiClass = declaration.getJavaPsi();

        // Check if it has an enclosing class (i.e., it is an inner/nested/anonymous class)
        PsiElement containingClass = psiClass.getContainingClass();
        if (containingClass == null) {
            // Top-level class, no leak possible
            return;
        }

        // If it's a static nested class, no leak
        if (!(declaration instanceof UAnonymousClass)) {
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
        }

        // Report the issue on the class declaration
        String message = "This Handler class should be static or leaks might occur (" +
                declaration.getName() + ")";

        if (declaration instanceof UAnonymousClass) {
            // For anonymous classes, report on the new expression / object creation
            UElement locationNode = declaration;
            context.report(ISSUE, declaration, context.getLocation(locationNode), message);
        } else {
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
        }
    }
}