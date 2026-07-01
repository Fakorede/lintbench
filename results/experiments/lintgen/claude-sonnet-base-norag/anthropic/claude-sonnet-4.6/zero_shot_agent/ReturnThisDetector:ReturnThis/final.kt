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
        private const val ANNOTATION_RETURN_THIS = "androidx.annotation.ReturnThis"

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

    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION_RETURN_THIS)

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

        // Check if the method itself also has @ReturnThis — if so, it's fine,
        // the annotation propagates and callers will be checked.
        // But we still need to verify that THIS method returns `this`.

        // Walk the method body and collect all return statements.
        // Every return statement must return `this`.
        val body = method.uastBody ?: return

        // If the method has no body (abstract/interface), skip.
        val visitor = ReturnThisVisitor(context, method)
        body.accept(visitor)
    }

    private class ReturnThisVisitor(
        private val context: JavaContext,
        private val method: UMethod
    ) : AbstractUastVisitor() {

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val returnValue = node.returnExpression

            if (returnValue == null) {
                // void return or bare return — not returning `this`
                // Only flag if the method has a non-void return type
                if (!isVoidMethod()) {
                    report(node)
                }
                return super.visitReturnExpression(node)
            }

            // Check if the return value is `this`
            if (!isThisExpression(returnValue)) {
                report(node)
            }

            return super.visitReturnExpression(node)
        }

        private fun isVoidMethod(): Boolean {
            val psiMethod = method.javaPsi
            val returnType = psiMethod.returnType ?: return true
            return returnType.canonicalText == "void"
        }

        private fun isThisExpression(element: UElement): Boolean {
            return element is UThisExpression
        }

        private fun report(node: UReturnExpression) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "This method must return `this` (as indicated by the `@ReturnThis` annotation on the overridden method)"
            )
        }
    }
}