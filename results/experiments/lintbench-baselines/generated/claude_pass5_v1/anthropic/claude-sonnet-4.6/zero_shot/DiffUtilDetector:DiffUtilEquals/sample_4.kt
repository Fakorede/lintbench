/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
            moreInfo = "https://issuetracker.google.com/116789824"
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIFF_UTIL_CALLBACK, DIFF_UTIL_CALLBACK_OLD)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods
        for (method in methods) {
            if (method.name == ARE_CONTENTS_THE_SAME) {
                checkMethod(context, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious equality check: did you mean `.equals()` instead of `==`?"
                    )
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "equals") {
                    checkEqualsCall(context, node)
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun checkEqualsCall(context: JavaContext, call: UCallExpression) {
        val receiverType = call.receiverType ?: return
        val psiClass = getPsiClass(context, receiverType) ?: return

        if (!hasCustomEquals(context, psiClass)) {
            val typeName = receiverType.presentableText
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Suspicious equality check: `$typeName` does not implement `equals()`"
            )
        }
    }

    private fun getPsiClass(context: JavaContext, type: PsiType): PsiClass? {
        return context.evaluator.getTypeClass(type)
    }

    private fun hasCustomEquals(context: JavaContext, psiClass: PsiClass): Boolean {
        // Check if the class itself or any of its superclasses (other than Object) override equals
        var current: PsiClass? = psiClass
        while (current != null) {
            val qualifiedName = current.qualifiedName
            // Stop at java.lang.Object - it has equals but it's identity-based
            if (qualifiedName == "java.lang.Object") {
                return false
            }
            // Check if this class declares equals
            val methods = current.findMethodsByName("equals", false)
            for (method in methods) {
                val params = method.parameterList.parameters
                if (params.size == 1 && params[0].type.canonicalText == "java.lang.Object") {
                    return true
                }
            }
            // Check if it's a data class (Kotlin data classes always have equals)
            if (isKotlinDataClass(current)) {
                return true
            }
            current = current.superClass
        }
        return false
    }

    private fun isKotlinDataClass(psiClass: PsiClass): Boolean {
        // Kotlin data classes have @kotlin.Metadata annotation with a flag indicating data class
        // We can check for the presence of componentN methods or copy method as a heuristic
        val modifierList = psiClass.modifierList ?: return false
        // Check for @kotlin.Metadata annotation
        val annotations = modifierList.annotations
        for (annotation in annotations) {
            if (annotation.qualifiedName == "kotlin.Metadata") {
                return true // Simplified: any Kotlin class - we'll rely on equals method check
            }
        }
        return false
    }
}