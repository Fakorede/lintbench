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
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

/**
 * Checks that Handler classes do not hold implicit references to their outer class,
 * which can prevent garbage collection and cause memory leaks.
 */
public class HandlerDetector extends Detector implements SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "HandlerLeak",
            "Handler reference leaks",
            "Since this `Handler` is declared as an inner class, it may prevent the " +
            "outer class from being garbage collected. If the `Handler` is " +
            "using a `Looper` or `MessageQueue` for a thread other than the " +
            "main thread, then there is no issue. If the `Handler` is using " +
            "the `Looper` or `MessageQueue` of the main thread, you need to " +
            "fix your `Handler` declaration, as follows: Declare the " +
            "`Handler` as a static class; In the outer class, instantiate a " +
            "`WeakReference` to the outer class and pass this object to your " +
            "`Handler` when you instantiate the `Handler`; Make all " +
            "references to members of the outer class using the " +
            "`WeakReference` object.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(
                    HandlerDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String HANDLER_CLASS = "android.os.Handler";

    /** Constructs a new {@link HandlerDetector} */
    public HandlerDetector() {
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HANDLER_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this class is an inner class (non-static) or anonymous class
        // that extends Handler.

        // Anonymous classes are always inner (non-static) classes
        boolean isAnonymous = declaration instanceof UAnonymousClass;

        if (!isAnonymous) {
            // For named classes, check if it's a non-static inner class
            if (!isInnerClass(declaration)) {
                // It's a top-level or static nested class — no leak possible
                return;
            }

            // Check if the class is static
            if (context.getEvaluator().isStatic(declaration)) {
                // Static nested class — no implicit reference to outer class
                return;
            }
        }

        // At this point, we have either:
        // 1. An anonymous class extending Handler (always an inner class)
        // 2. A non-static named inner class extending Handler

        // Check that it actually has an outer class (i.e., it's truly an inner class)
        UElement parent = declaration.getUastParent();
        if (parent == null) {
            return;
        }

        // Find the containing class
        UClass containingClass = getContainingClass(declaration);
        if (containingClass == null) {
            return;
        }

        // Report the issue
        String message;
        if (isAnonymous) {
            message = "This `Handler` class should be static or leaks might occur (" +
                    containingClass.getName() + ")";
        } else {
            message = "This `Handler` class should be static or leaks might occur (" +
                    containingClass.getName() + ")";
        }

        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    /**
     * Returns true if the given class is a named inner class (i.e., it has an enclosing class).
     */
    private static boolean isInnerClass(@NonNull UClass declaration) {
        UElement parent = declaration.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return true;
            }
            parent = parent.getUastParent();
        }
        return false;
    }

    /**
     * Returns the immediately containing class of the given class declaration, or null if none.
     */
    private static UClass getContainingClass(@NonNull UClass declaration) {
        UElement parent = declaration.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return (UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }
}