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
import com.android.tools.lint.detector.api.Context
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

        /** The set of internal inset resource names that should not be used directly. */
        private val INTERNAL_INSET_DIMEN_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height_landscape",
        )

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

        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val GET_IDENTIFIER_METHOD = "getIdentifier"
        private const val GET_DIMENSION_PIXEL_SIZE_METHOD = "getDimensionPixelSize"
        private const val GET_DIMENSION_METHOD = "getDimension"
        private const val GET_DIMENSION_PIXEL_OFFSET_METHOD = "getDimensionPixelOffset"

        private val RESOURCE_GET_METHODS = setOf(
            GET_DIMENSION_PIXEL_SIZE_METHOD,
            GET_DIMENSION_METHOD,
            GET_DIMENSION_PIXEL_OFFSET_METHOD,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            GET_DIMENSION_PIXEL_SIZE_METHOD,
            GET_DIMENSION_METHOD,
            GET_DIMENSION_PIXEL_OFFSET_METHOD,
            GET_IDENTIFIER_METHOD,
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        // Check if the method is called on an android.content.res.Resources instance
        if (!context.evaluator.isMemberInClass(method, RESOURCES_CLASS)) {
            return
        }

        if (methodName == GET_IDENTIFIER_METHOD) {
            checkGetIdentifierCall(context, node)
        } else if (methodName in RESOURCE_GET_METHODS) {
            checkResourceGetCall(context, node)
        }
    }

    /**
     * Checks calls to Resources.getIdentifier() where the name argument is one of the
     * internal inset resource names.
     */
    private fun checkGetIdentifierCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val nameArg = arguments[0]
        val nameValue = nameArg.evaluate() as? String ?: return

        if (nameValue in INTERNAL_INSET_DIMEN_NAMES) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                buildMessage(nameValue)
            )
        }
    }

    /**
     * Checks calls to Resources.getDimensionPixelSize(), getDimension(), or
     * getDimensionPixelOffset() where the resource ID argument refers to one of the
     * internal inset dimension resources.
     */
    private fun checkResourceGetCall(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val resIdArg = arguments[0]
        val resourceName = resolveInternalInsetResourceName(resIdArg) ?: return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            buildMessage(resourceName)
        )
    }

    /**
     * Attempts to resolve whether the given expression refers to an internal Android
     * inset dimen resource (e.g., `android.R.dimen.status_bar_height`).
     * Returns the resource name if it does, or null otherwise.
     */
    private fun resolveInternalInsetResourceName(expression: UExpression): String? {
        // We look for patterns like:
        //   android.R.dimen.status_bar_height
        //   R.dimen.status_bar_height  (when the R class belongs to android package)

        if (expression is UQualifiedReferenceExpression) {
            return resolveQualifiedReference(expression)
        }

        // Handle simple name reference that might resolve to android.R.dimen.*
        if (expression is USimpleNameReferenceExpression) {
            val name = expression.identifier
            if (name in INTERNAL_INSET_DIMEN_NAMES) {
                // Try to resolve through the PsiElement
                val resolved = expression.resolve()
                if (resolved != null) {
                    val qualifiedName = (resolved as? com.intellij.psi.PsiField)
                        ?.containingClass?.qualifiedName
                    if (qualifiedName == "android.R.dimen") {
                        return name
                    }
                }
            }
        }

        return null
    }

    /**
     * Resolves a qualified reference expression to check if it refers to an internal
     * inset resource. Returns the resource name if matched, null otherwise.
     */
    private fun resolveQualifiedReference(expression: UQualifiedReferenceExpression): String? {
        // The expression could be:
        //   android.R.dimen.status_bar_height
        //     selector: android.R.dimen
        //     member: status_bar_height
        // or
        //   R.dimen.status_bar_height
        //     selector: R.dimen
        //     member: status_bar_height

        val selector = expression.selector
        val receiver = expression.receiver

        // Get the field name (last segment)
        val fieldName = when (selector) {
            is USimpleNameReferenceExpression -> selector.identifier
            else -> return null
        }

        if (fieldName !in INTERNAL_INSET_DIMEN_NAMES) return null

        // Now check that the receiver resolves to android.R.dimen
        if (isAndroidRDimen(receiver)) {
            return fieldName
        }

        // Also try resolving the field itself
        val resolvedField = (selector as? USimpleNameReferenceExpression)?.resolve()
            as? com.intellij.psi.PsiField
        if (resolvedField != null) {
            val containingClass = resolvedField.containingClass
            if (containingClass?.qualifiedName == "android.R.dimen") {
                return fieldName
            }
        }

        return null
    }

    /**
     * Returns true if the given expression refers to `android.R.dimen`.
     */
    private fun isAndroidRDimen(expression: UExpression): Boolean {
        if (expression is UQualifiedReferenceExpression) {
            val selector = expression.selector
            val receiver = expression.receiver

            val selectorName = (selector as? USimpleNameReferenceExpression)?.identifier
            if (selectorName == "dimen") {
                // Check receiver is android.R or R (from android package)
                return isAndroidR(receiver)
            }
        }
        return false
    }

    /**
     * Returns true if the given expression refers to `android.R` or `R` in the android package.
     */
    private fun isAndroidR(expression: UExpression): Boolean {
        if (expression is UQualifiedReferenceExpression) {
            // android.R
            val selector = expression.selector
            val receiver = expression.receiver
            val selectorName = (selector as? USimpleNameReferenceExpression)?.identifier
            if (selectorName == "R") {
                val receiverName = getFullyQualifiedName(receiver)
                if (receiverName == "android") return true
            }
        } else if (expression is USimpleNameReferenceExpression) {
            if (expression.identifier == "R") {
                // Could be android.R imported as R - check the resolved class
                val resolved = expression.resolve()
                if (resolved is com.intellij.psi.PsiClass) {
                    if (resolved.qualifiedName == "android.R") return true
                }
            }
        }
        return false
    }

    /**
     * Gets the fully qualified name from a reference expression chain.
     */
    private fun getFullyQualifiedName(expression: UExpression): String? {
        return when (expression) {
            is USimpleNameReferenceExpression -> expression.identifier
            is UQualifiedReferenceExpression -> {
                val receiverName = getFullyQualifiedName(expression.receiver) ?: return null
                val selectorName =
                    (expression.selector as? USimpleNameReferenceExpression)?.identifier
                        ?: return null
                "$receiverName.$selectorName"
            }
            else -> null
        }
    }

    private fun buildMessage(resourceName: String): String {
        return "Avoid using internal inset resource `$resourceName`. " +
            "The insets are dynamic values that can change while your app is visible, " +
            "and your app's window may not intersect with the system UI. " +
            "Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
    }
}