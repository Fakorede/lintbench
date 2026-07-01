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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.util.isMethodCall

/**
 * Checks for missing explicit casts on `findViewById` calls that may be
 * required when compiling with Java 8 language level.
 */
class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, \
                which means that most of the time you can leave out explicit casts and \
                just assign the result of the `findViewById` call to variables of specific \
                view classes.

                However, due to language changes between Java 7 and 8, this change may \
                cause code to not compile without explicit casts. This lint check looks \
                for these scenarios and suggests casts to be added now such that the code \
                will continue to compile if the language level is updated to 1.8.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val VIEW_CLASS = "android.view.View"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Only care about calls to View.findViewById or Activity.findViewById etc.
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        // Make sure this is a findViewById method on a View or Activity (or similar)
        if (!evaluator.extendsClass(containingClass, VIEW_CLASS, false) &&
            !evaluator.extendsClass(containingClass, "android.app.Activity", false) &&
            !evaluator.extendsClass(containingClass, "android.app.Dialog", false) &&
            !evaluator.extendsClass(containingClass, "androidx.fragment.app.Fragment", false) &&
            !evaluator.extendsClass(containingClass, "android.support.v4.app.Fragment", false)
        ) {
            return
        }

        // Check the return type of the method — if it's already generic (i.e., not View),
        // this is the new API and we don't need to warn.
        val returnType = method.returnType?.canonicalText ?: return
        // Old API returns android.view.View; new generic API returns T
        if (returnType != VIEW_CLASS) {
            // Already using generics — no issue
            return
        }

        // Now check the parent expression to see if there's a cast
        val uastParent = node.uastParent ?: return

        // Unwrap parenthesized expressions
        val effectiveParent = if (uastParent is UParenthesizedExpression) {
            uastParent.uastParent ?: return
        } else {
            uastParent
        }

        // If the parent is a cast expression, everything is fine
        if (effectiveParent is org.jetbrains.uast.UBinaryExpressionWithType) {
            // Already has an explicit cast
            return
        }

        // Check if assigned to a variable with a specific View subtype
        when (effectiveParent) {
            is UVariable -> {
                val declaredType = effectiveParent.type
                val typeName = declaredType.canonicalText
                if (typeName == VIEW_CLASS || typeName == "java.lang.Object") {
                    // Assigned to View or Object — no cast needed
                    return
                }
                // Check if it's a View subtype
                val psiClass = evaluator.findClass(typeName)
                if (psiClass != null && evaluator.extendsClass(psiClass, VIEW_CLASS, false)) {
                    // Assigned to a View subtype without a cast — warn
                    reportMissingCast(context, node, typeName)
                }
            }
            is UReturnExpression -> {
                // Returned from a method — check the method's return type
                val containingMethod = getContainingMethod(node) ?: return
                val methodReturnType = containingMethod.returnType?.canonicalText ?: return
                if (methodReturnType == VIEW_CLASS || methodReturnType == "java.lang.Object") {
                    return
                }
                val psiClass = evaluator.findClass(methodReturnType)
                if (psiClass != null && evaluator.extendsClass(psiClass, VIEW_CLASS, false)) {
                    reportMissingCast(context, node, methodReturnType)
                }
            }
            else -> {
                // Could be passed as argument, etc. — check if it's used in a context
                // that would require an implicit cast.
                checkImplicitCastContext(context, node, effectiveParent)
            }
        }
    }

    private fun checkImplicitCastContext(
        context: JavaContext,
        node: UCallExpression,
        parent: UExpression
    ) {
        // For method call arguments, we could check parameter types,
        // but that's complex. The main cases are variable assignments
        // and return statements, handled above.
    }

    private fun reportMissingCast(
        context: JavaContext,
        node: UCallExpression,
        castType: String
    ) {
        val simpleName = castType.substringAfterLast('.')
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast here; won't compile with Java language level 8 " +
                "without it: `($simpleName) ${node.asSourceString()}`"
        )
    }

    private fun getContainingMethod(node: UCallExpression): PsiMethod? {
        var current = node.uastParent
        while (current != null) {
            if (current is org.jetbrains.uast.UMethod) {
                return current.javaPsi
            }
            current = current.uastParent
        }
        return null
    }
}