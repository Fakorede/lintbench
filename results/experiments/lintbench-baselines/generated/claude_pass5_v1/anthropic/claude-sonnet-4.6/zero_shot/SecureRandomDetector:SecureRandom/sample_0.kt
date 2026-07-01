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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UNewExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isNewArrayWithInitializer

/** Checks for hardcoded seeds with SecureRandom. */
class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            moreInfo = "https://goo.gle/SecureRandom",
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val ADDITIONAL_INFO =
            "https://developer.android.com/reference/java/security/SecureRandom.html"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val seedArgument = arguments[0]
        if (isFixedSeed(seedArgument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                    "it is not secure. Use `getSeed()`."
            )
        }
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SECURE_RANDOM_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        // SecureRandom(byte[] seed) constructor with a fixed seed
        val seedArgument = arguments[0]
        if (isFixedSeed(seedArgument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do not call `new SecureRandom(seed)` with a fixed seed: " +
                    "it is not secure. Use `new SecureRandom()`."
            )
        }
    }

    /**
     * Returns true if the given expression represents a fixed/hardcoded seed value.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        // Literal values (numbers, strings) are fixed seeds
        if (expression is ULiteralExpression) {
            return true
        }

        // New byte array with initializer: new byte[] { 1, 2, 3 }
        if (expression.isNewArrayWithInitializer()) {
            val newArray = expression as UNewExpression
            val initializers = newArray.valueArguments
            // An empty array or an array with literal values is a fixed seed
            if (initializers.isEmpty()) {
                return true
            }
            return initializers.all { it is ULiteralExpression }
        }

        // Check for new array expressions with dimensions
        if (expression is UNewExpression) {
            val type = expression.type
            if (type != null && type.canonicalText.contains("byte")) {
                // new byte[N] - fixed size empty byte array
                return true
            }
        }

        // References to static final fields (constants) are fixed seeds
        if (expression is UReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is com.intellij.psi.PsiField) {
                if (resolved.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) &&
                    resolved.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)
                ) {
                    return true
                }
            }
        }

        return false
    }
}