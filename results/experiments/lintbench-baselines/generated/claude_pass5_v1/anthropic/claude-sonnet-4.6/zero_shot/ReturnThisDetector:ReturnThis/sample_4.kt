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

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            if (!methodRequiresReturnThis(context, node)) {
                return false
            }

            // Walk the method body and check all return statements
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitReturnExpression(node: UReturnExpression): Boolean {
                    val returnValue = node.returnExpression
                    if (returnValue == null || returnValue !is UThisExpression) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "This method must return `this` (as documented by the `@ReturnThis` annotation)"
                        )
                    }
                    return false
                }
            })

            return false
        }
    }

    private fun methodRequiresReturnThis(context: JavaContext, method: UMethod): Boolean {
        // Check if the method itself is annotated with @ReturnThis
        if (hasReturnThisAnnotation(context, method)) {
            return true
        }

        // Check if any super method is annotated with @ReturnThis
        val psiMethod = method.javaPsi
        return superMethodsHaveReturnThis(context, psiMethod)
    }

    private fun superMethodsHaveReturnThis(context: JavaContext, method: PsiMethod): Boolean {
        val superMethods = method.findSuperMethods()
        for (superMethod in superMethods) {
            val uMethod = context.uastContext.getMethod(superMethod)
            if (hasReturnThisAnnotation(context, uMethod)) {
                return true
            }
            // Check annotations directly on the PsiMethod as well
            if (superMethod.annotations.any { annotation ->
                    annotation.qualifiedName == RETURN_THIS_ANNOTATION
                }) {
                return true
            }
            // Recursively check super methods
            if (superMethodsHaveReturnThis(context, superMethod)) {
                return true
            }
        }
        return false
    }

    private fun hasReturnThisAnnotation(context: JavaContext, annotated: UAnnotated?): Boolean {
        if (annotated == null) return false
        return context.evaluator.getAllAnnotations(annotated, inHierarchy = false)
            .any { it.qualifiedName == RETURN_THIS_ANNOTATION }
    }
}