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

    override fun getApplicableMethodNames(): List<String> = listOf()

    override fun applicableSuperClasses(): List<String>? = null

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : com.android.tools.lint.client.api.UElementHandler() {
        override fun visitMethod(node: UMethod) {
            // Find the super method this overrides
            val superMethod = findSuperMethodWithEmptySuper(context, node) ?: return

            // Check if the method body calls super.methodName(...)
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (isSuperCall(node, superMethod)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "No need to call `super.${superMethod.name}()`; the super method is defined to be empty"
                        )
                    }
                    return super.visitCallExpression(node)
                }
            })
        }
    }

    private fun findSuperMethodWithEmptySuper(context: JavaContext, method: UMethod): PsiMethod? {
        val psiMethod = method.javaPsi
        // Get all super methods
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

    private fun isSuperCall(call: UCallExpression, superMethod: PsiMethod): Boolean {
        // Check that the receiver is a super expression
        val receiver = call.receiver
        if (receiver !is USuperExpression) return false
        // Check that the method name matches
        if (call.methodName != superMethod.name) return false
        // Optionally resolve to verify it's the same method
        val resolved = call.resolve()
        if (resolved != null && resolved != superMethod) return false
        return true
    }
}