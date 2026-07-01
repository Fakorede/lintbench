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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not \
                also call the super implementation, either because it is empty, or perhaps \
                it contains code not intended to be run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            // Find the super method this method overrides
            val superMethod = findSuperMethodWithEmptySuper(context, node) ?: return false

            // Now look for super calls within this method body
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val receiver = node.receiver
                    if (receiver is USuperExpression) {
                        val resolvedMethod = node.resolve()
                        if (resolvedMethod != null && resolvedMethod == superMethod) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "No need to call `super.${node.methodName}()`; the super method is defined to be empty"
                            )
                        }
                    }
                    return false
                }
            })

            return false
        }

        private fun findSuperMethodWithEmptySuper(context: JavaContext, method: UMethod): PsiMethod? {
            val psiMethod = method.javaPsi
            // Get direct super methods
            val superMethods = psiMethod.findSuperMethods()
            for (superMethod in superMethods) {
                if (context.evaluator.getAllAnnotations(superMethod, inHierarchy = false)
                        .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
                ) {
                    return superMethod
                }
            }
            return null
        }
    }
}