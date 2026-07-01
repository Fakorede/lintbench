/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PKG
import com.android.resources.ResourceType
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.util.isMethodCall

/**
 * Detector that flags usage of internal inset dimension resources such as
 * `android.R.dimen.status_bar_height`, `android.R.dimen.navigation_bar_height`, etc.
 */
class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {

        /** The set of known internal inset dimension resource names. */
        private val INSET_DIMEN_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_frame_height",
            "taskbar_frame_height",
        )

        private const val MESSAGE =
            "Using internal inset dimension resources is not supported. " +
                "Insets are dynamic values that can change while your app is visible, " +
                "and your app's window may not intersect with the system UI. " +
                "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI.
                To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        // Resource lookup method names
        private val DIMEN_RESOURCE_METHODS = setOf(
            "getDimensionPixelSize",
            "getDimensionPixelOffset",
            "getDimension",
        )

        // Classes that have resource-lookup methods we care about
        private val RESOURCE_CLASSES = setOf(
            "android.content.res.Resources",
            "android.content.Context",
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return DIMEN_RESOURCE_METHODS.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        if (containingClass !in RESOURCE_CLASSES &&
            !isSubclassOfResourceClass(context, method)
        ) {
            return
        }

        // The first argument should be the resource ID
        val args = node.valueArguments
        if (args.isEmpty()) return

        val resourceArg = args[0]

        if (isInternalInsetResource(resourceArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(resourceArg),
                MESSAGE
            )
        }
    }

    /**
     * Check if the method belongs to a subclass of Resources or Context.
     */
    private fun isSubclassOfResourceClass(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        val evaluator = context.evaluator
        return evaluator.extendsClass(containingClass, "android.content.res.Resources", false) ||
            evaluator.extendsClass(containingClass, "android.content.Context", false)
    }

    /**
     * Checks whether the given expression resolves to an internal inset resource reference
     * of the form `android.R.dimen.<inset_name>`.
     */
    private fun isInternalInsetResource(expression: UExpression): Boolean {
        // We're looking for references like android.R.dimen.status_bar_height
        // which in UAST typically appears as a qualified reference expression or
        // a simple field reference that resolves to an android.R.dimen field.

        val text = expression.asSourceString()

        // Quick check: does the text contain any of the inset names?
        val matchedName = INSET_DIMEN_NAMES.firstOrNull { text.contains(it) } ?: return false

        // Now verify it's actually referencing android.R.dimen.<name>
        return isAndroidRDimenReference(expression, matchedName)
    }

    /**
     * Verifies that the expression is a reference to `android.R.dimen.<resourceName>`.
     */
    private fun isAndroidRDimenReference(expression: UExpression, resourceName: String): Boolean {
        // Handle qualified references like android.R.dimen.status_bar_height
        if (expression is UQualifiedReferenceExpression) {
            val selector = expression.selector
            if (selector is USimpleNameReferenceExpression) {
                if (selector.identifier != resourceName) return false
            }
            val receiver = expression.receiver
            return isAndroidRDimenQualifier(receiver)
        }

        // Handle simple name references that resolve to the field
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved != null) {
                // Try to get the containing class to check it's android.R.dimen
                if (resolved is com.intellij.psi.PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null) {
                        val className = containingClass.qualifiedName
                        if (className == "android.R.dimen" || className == "android.R\$dimen") {
                            return resolved.name in INSET_DIMEN_NAMES
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Checks if the given expression represents `android.R.dimen`.
     */
    private fun isAndroidRDimenQualifier(expression: UExpression): Boolean {
        if (expression is UQualifiedReferenceExpression) {
            val selector = expression.selector
            if (selector is USimpleNameReferenceExpression && selector.identifier == "dimen") {
                val receiver = expression.receiver
                return isAndroidRQualifier(receiver)
            }
        }
        return false
    }

    /**
     * Checks if the given expression represents `android.R`.
     */
    private fun isAndroidRQualifier(expression: UExpression): Boolean {
        if (expression is UQualifiedReferenceExpression) {
            val selector = expression.selector
            val receiver = expression.receiver
            if (selector is USimpleNameReferenceExpression && selector.identifier == "R") {
                if (receiver is UReferenceExpression) {
                    return receiver.asSourceString() == "android"
                }
            }
        }
        // Handle just "R" reference that resolves to android.R
        if (expression is USimpleNameReferenceExpression && expression.identifier == "R") {
            return true // could be android.R imported
        }
        return false
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return INSET_DIMEN_NAMES.toList()
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        if (referenced !is com.intellij.psi.PsiField) return

        val containingClass = referenced.containingClass ?: return
        val className = containingClass.qualifiedName ?: return

        // Check it's android.R.dimen (inner class notation uses $ in bytecode but . in source)
        if (className != "android.R.dimen" && className != "android.R\$dimen") return

        val fieldName = referenced.name
        if (fieldName !in INSET_DIMEN_NAMES) return

        // Make sure this reference is used as an argument to a resource lookup method
        // Walk up the UAST tree to see if it's inside a getDimensionPixelSize/etc. call
        var parent: UElement? = reference.uastParent
        while (parent != null) {
            if (parent is UCallExpression && parent.isMethodCall()) {
                val methodName = parent.methodName
                if (methodName != null && methodName in DIMEN_RESOURCE_METHODS) {
                    // Already handled by visitMethodCall; skip to avoid double-reporting
                    return
                }
                break
            }
            // Stop climbing if we've gone too far
            if (parent is UCallExpression || parent is com.intellij.psi.PsiFile) break
            parent = parent.uastParent
        }

        // Report the reference usage regardless of whether it's in a method call
        // (e.g., storing the resource ID in a variable)
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            MESSAGE
        )
    }
}