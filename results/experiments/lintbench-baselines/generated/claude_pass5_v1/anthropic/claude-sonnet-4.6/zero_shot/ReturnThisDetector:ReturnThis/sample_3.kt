/*
 * Copyright (C) 2023 The Android Open Source Project
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
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this \
                method is overriding) should also `return this`.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): ReturnThisVisitor =
        ReturnThisVisitor(context)

    inner class ReturnThisVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            if (!shouldCheckMethod(node)) return false

            // Walk through all return statements in the method body
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitReturnExpression(node: UReturnExpression): Boolean {
                    val returnValue = node.returnExpression
                    if (returnValue == null || !isThisExpression(returnValue)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "This method must return `this` (as annotated with `@ReturnThis`)"
                        )
                    }
                    return false
                }

                // Don't descend into nested lambdas/anonymous classes
                override fun visitCallExpression(node: UCallExpression): Boolean = false
            })

            return false
        }

        /**
         * Determines whether the given method should be checked for @ReturnThis compliance.
         * A method should be checked if it or any method it overrides is annotated with @ReturnThis.
         */
        private fun shouldCheckMethod(node: UMethod): Boolean {
            // Check if the method itself is annotated
            if (hasReturnThisAnnotation(node)) return true

            // Check if any overridden method is annotated
            val evaluator = context.evaluator
            val superMethods = evaluator.getSuperMethods(node.javaPsi)
            return superMethods.any { superMethod ->
                hasReturnThisAnnotation(superMethod)
            }
        }

        private fun hasReturnThisAnnotation(annotated: UAnnotated): Boolean {
            return annotated.findAnnotation(RETURN_THIS_ANNOTATION) != null
        }

        private fun hasReturnThisAnnotation(method: PsiMethod): Boolean {
            return context.evaluator.getAllAnnotations(method, inHierarchy = false)
                .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
        }

        private fun isThisExpression(element: UElement): Boolean {
            return element is UThisExpression
        }
    }
}