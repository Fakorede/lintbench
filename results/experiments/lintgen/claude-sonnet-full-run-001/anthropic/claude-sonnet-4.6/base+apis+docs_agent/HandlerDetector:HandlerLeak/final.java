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
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

/**
 * Checks for potential Handler memory leaks caused by non-static inner Handler classes.
 */
public class HandlerDetector extends Detector implements SourceCodeScanner {

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

    private static final String HANDLER_CLASS = "android.os.Handler";

    /** Constructs a new {@link HandlerDetector} */
    public HandlerDetector() {
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HANDLER_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this class is an inner class (not static)
        if (isInnerClass(declaration)) {
            UElement locationElement = declaration.getNameIdentifier() != null
                    ? declaration
                    : declaration;
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation((UElement) declaration),
                    "This `Handler` class should be static or leaks might occur ("
                            + getDescription(declaration) + ")");
        }
    }

    private static boolean isInnerClass(@NonNull UClass declaration) {
        // Anonymous classes are always inner classes
        if (declaration instanceof UAnonymousClass) {
            // Check if it has an enclosing class
            UElement parent = declaration.getUastParent();
            while (parent != null) {
                if (parent instanceof UClass) {
                    return true;
                }
                parent = parent.getUastParent();
            }
            return false;
        }

        // For named classes, check if they are non-static inner classes
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return false;
        }

        // Check if it has a containing class
        PsiClass containingClass = psiClass.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // If it's static, it won't leak
        if (declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        return true;
    }

    @NonNull
    private static String getDescription(@NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            return "anonymous extends Handler";
        }
        String name = declaration.getName();
        if (name != null) {
            return name;
        }
        return "extends Handler";
    }
}