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
 * Checks for potential Handler memory leaks.
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

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_OS_HANDLER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this class is a non-static inner class or anonymous class
        if (!isInnerClass(declaration)) {
            return;
        }

        // Check if the class is static
        if (isStaticClass(declaration)) {
            return;
        }

        // Check if it has an enclosing class (it must be an inner class, not a top-level class)
        UElement parent = getEnclosingClass(declaration);
        if (parent == null) {
            return;
        }

        String message = "This Handler class should be static or leaks might occur (" +
                getHandlerName(declaration) + ")";

        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    /**
     * Returns the display name of the handler class for use in the error message.
     */
    private static String getHandlerName(@NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            return "anonymous android.os.Handler";
        }
        String name = declaration.getName();
        if (name != null) {
            return name;
        }
        return "anonymous android.os.Handler";
    }

    /**
     * Checks whether the given class is an inner class (i.e., has an enclosing class).
     */
    private static boolean isInnerClass(@NonNull UClass declaration) {
        // Anonymous classes are always inner classes
        if (declaration instanceof UAnonymousClass) {
            UElement parent = declaration.getUastParent();
            while (parent != null) {
                if (parent instanceof UClass) {
                    return true;
                }
                parent = parent.getUastParent();
            }
            return false;
        }

        // For named classes, check if there is a containing class
        PsiElement psiElement = declaration.getPsi();
        if (psiElement instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) psiElement;
            return psiClass.getContainingClass() != null;
        }

        // Fallback: check UAST parent chain
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
     * Checks whether the given class is declared as static.
     */
    private static boolean isStaticClass(@NonNull UClass declaration) {
        PsiModifierList modifierList = declaration.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        // Anonymous classes cannot be static
        if (declaration instanceof UAnonymousClass) {
            return false;
        }
        return false;
    }

    /**
     * Returns the enclosing UClass of the given class declaration, if any.
     */
    private static UElement getEnclosingClass(@NonNull UClass declaration) {
        UElement parent = declaration.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }
}