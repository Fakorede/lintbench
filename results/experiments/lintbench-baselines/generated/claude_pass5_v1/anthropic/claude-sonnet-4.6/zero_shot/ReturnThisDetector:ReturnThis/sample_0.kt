/*
 * Copyright (C) 2022 The Android Open Source Project
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
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` (usually in the super method that this \
                method is overriding) should also `return this`.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
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
        // element here is the overriding method
        val method = element as? UMethod ?: return

        // Check all return statements in this method
        val visitor = ReturnThisVisitor(method)
        method.accept(visitor)

        if (visitor.hasNonThisReturn) {
            val location = context.getNameLocation(method)
            context.report(
                ISSUE,
                method,
                location,
                "This method must return `this` (as indicated by the `@ReturnThis` annotation on the overridden method)"
            )
        }
    }

    /**
     * Visitor that checks whether any return statement in a method does NOT return `this`.
     * Note: we skip nested methods/lambdas so we only look at the top-level method's returns.
     */
    private class ReturnThisVisitor(private val targetMethod: UMethod) : AbstractUastVisitor() {

        var hasNonThisReturn = false

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val returnValue = node.returnExpression
            if (returnValue == null || returnValue !is UThisExpression) {
                hasNonThisReturn = true
            }
            return super.visitReturnExpression(node)
        }

        // Don't descend into nested methods or lambdas
        override fun visitMethod(node: UMethod): Boolean {
            if (node == targetMethod) {
                // Visit the target method itself
                return false // false = continue visiting children
            }
            // For nested methods, skip them
            return true
        }
    }
}