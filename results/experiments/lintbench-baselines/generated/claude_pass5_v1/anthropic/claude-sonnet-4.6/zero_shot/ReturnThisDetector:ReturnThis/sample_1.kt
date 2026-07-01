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
        // element should be the overriding method
        val method = element as? UMethod ?: return

        // Check if the method itself is also annotated with @ReturnThis;
        // if so, we don't need to flag it (it will be checked when its overriders are visited)
        // but we still need to validate its own body returns `this`.
        // Actually, we should still validate the body returns `this` regardless.

        // Walk the method body looking for return statements that don't return `this`
        val body = method.uastBody ?: return

        // Collect all return expressions
        val returnChecker = ReturnThisChecker()
        body.accept(returnChecker)

        if (returnChecker.hasNonThisReturn) {
            val location = context.getLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "This method must return `this` (it is annotated with or overrides a method " +
                    "annotated with `@ReturnThis`)"
            )
        }
    }

    /**
     * Visits a method body and checks whether any return statement returns something
     * other than `this`.
     */
    private class ReturnThisChecker : AbstractUastVisitor() {
        var hasNonThisReturn = false

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val returnValue = node.returnExpression
            if (returnValue == null || returnValue !is UThisExpression) {
                hasNonThisReturn = true
            }
            return super.visitReturnExpression(node)
        }

        // Don't recurse into nested lambdas/anonymous classes
        override fun visitMethod(node: UMethod): Boolean {
            return true // skip body of nested methods
        }
    }
}