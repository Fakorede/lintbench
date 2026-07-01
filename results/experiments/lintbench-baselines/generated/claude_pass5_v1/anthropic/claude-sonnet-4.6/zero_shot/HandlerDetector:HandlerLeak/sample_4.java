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
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

/**
 * Checks that Handler implementations don't hold implicit references to outer classes that
 * could prevent garbage collection (Handler leak).
 */
public class HandlerDetector extends Detector implements Detector.UastScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this `Handler` is declared as an inner class, it may prevent the outer " +
            "class from being garbage collected. If the `Handler` is using a `Looper` or " +
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
        // Check if this is an anonymous class or a named inner class
        if (declaration instanceof UAnonymousClass) {
            // Anonymous class - check if it's inside another class and not static context
            checkHandlerClass(context, declaration);
        } else {
            // Named class - check if it's a non-static inner class
            PsiClass psiClass = declaration.getJavaPsi();
            if (psiClass == null) {
                return;
            }

            // Check if it has a containing class (i.e., it's an inner class)
            PsiClass containingClass = psiClass.getContainingClass();
            if (containingClass == null) {
                // Top-level class, no leak possible
                return;
            }

            // If it's static, no leak possible
            if (declaration.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }

            // It's a non-static inner class extending Handler - this is a leak
            reportIssue(context, declaration);
        }
    }

    private void checkHandlerClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // For anonymous classes, we need to check if they are enclosed in an outer class
        // and whether they are in a static context
        UElement parent = declaration.getUastParent();
        if (parent == null) {
            return;
        }

        // Walk up to find enclosing class
        UClass enclosingClass = UastUtils.getParentOfType(parent, UClass.class, false);
        if (enclosingClass == null) {
            // No enclosing class - no leak
            return;
        }

        // Anonymous inner classes always hold a reference to the outer class
        // (they can't be static), so report the issue
        reportIssue(context, declaration);
    }

    private void reportIssue(@NonNull JavaContext context, @NonNull UClass declaration) {
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This `Handler` class should be static or leaks might occur (" +
                getHandlerName(declaration) + ")");
    }

    private static String getHandlerName(@NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            return "anonymous android.os.Handler";
        }
        String name = declaration.getQualifiedName();
        if (name == null) {
            name = declaration.getName();
        }
        return name != null ? name : "android.os.Handler";
    }
}