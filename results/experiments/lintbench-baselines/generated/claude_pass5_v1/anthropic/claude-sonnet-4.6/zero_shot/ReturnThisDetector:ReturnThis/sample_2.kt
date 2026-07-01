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

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"

        @JvmField
        val ISSUE: Issue = Issue.create(
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

    override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        // element here should be the overriding method
        val method = element as? UMethod ?: return

        // Check if the overriding method itself is also annotated with @ReturnThis
        // (in which case subclasses will be checked, but this method itself still needs to comply)

        // Walk the method body and find all return statements
        // Each return statement must return "this"
        val body = method.uastBody ?: return

        // Check if there are any return statements that don't return `this`
        val visitor = ReturnThisVisitor(context, method)
        body.accept(visitor)
    }

    /**
     * Also handle the case where a method is directly annotated with @ReturnThis
     * (not just inherited). We need to check those methods too.
     */
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): com.android.tools.lint.client.api.UElementHandler {
        return object : com.android.tools.lint.client.api.UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Check if this method is directly annotated with @ReturnThis
                val hasDirectAnnotation = context.evaluator.getAllAnnotations(node as UAnnotated, false)
                    .any { it.qualifiedName == RETURN_THIS_ANNOTATION }

                if (!hasDirectAnnotation) return

                val body = node.uastBody ?: return
                val visitor = ReturnThisVisitor(context, node)
                body.accept(visitor)
            }
        }
    }

    private class ReturnThisVisitor(
        private val context: JavaContext,
        private val method: UMethod
    ) : AbstractUastVisitor() {

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            // Only check return statements directly in this method, not in lambdas/anonymous classes
            // Find the enclosing method of this return statement
            val enclosingMethod = findEnclosingMethod(node)
            if (enclosingMethod != method) {
                return false
            }

            val returnValue = node.returnExpression

            // If there's no return value (void), that's fine - we only care about non-void returns
            // But @ReturnThis methods should return something, so flag null returns too
            if (returnValue == null) {
                // void return - if the method returns something, this might be an implicit return
                // For methods annotated with @ReturnThis, we expect explicit `return this`
                // A bare `return;` in a non-void method would be a compile error anyway
                return false
            }

            // Check if the return value is `this`
            if (returnValue !is UThisExpression) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "This method must return `this` (as declared by the `@ReturnThis` annotation)"
                )
            }

            return false
        }

        private fun findEnclosingMethod(element: UElement): UMethod? {
            var current: UElement? = element.uastParent
            while (current != null) {
                if (current is UMethod) return current
                // Stop at anonymous classes / lambdas that introduce new method scopes
                current = current.uastParent
            }
            return null
        }
    }
}