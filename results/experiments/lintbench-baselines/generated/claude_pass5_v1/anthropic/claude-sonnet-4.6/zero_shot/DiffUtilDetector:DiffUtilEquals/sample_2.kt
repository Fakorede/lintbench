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

        private const val DIFF_UTIL_CLASS = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CLASS_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIFF_UTIL_CLASS, DIFF_UTIL_CLASS_OLD)
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
                    val leftType = node.leftOperand.getExpressionType()
                    if (leftType != null && !leftType.equalsToText("boolean") &&
                        !leftType.equalsToText("int") &&
                        !leftType.equalsToText("long") &&
                        !leftType.equalsToText("double") &&
                        !leftType.equalsToText("float") &&
                        !leftType.equalsToText("byte") &&
                        !leftType.equalsToText("char") &&
                        !leftType.equalsToText("short")
                    ) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Suspicious equality check: Did you mean `.equals()` instead of `==`?"
                        )
                    }
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "equals") {
                    val receiverType = node.receiverType
                    if (receiverType != null) {
                        checkEqualsCall(context, node, receiverType)
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun checkEqualsCall(
        context: JavaContext,
        node: UCallExpression,
        receiverType: PsiType
    ) {
        val evaluator = context.evaluator
        val psiClass = evaluator.getTypeClass(receiverType) ?: return

        if (hasCustomEquals(context, psiClass)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Suspicious equality check: `${psiClass.name}` does not override `equals()`"
        )
    }

    private fun hasCustomEquals(context: JavaContext, psiClass: PsiClass): Boolean {
        val evaluator = context.evaluator

        // Check if it's a known class that has equals implemented
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == "java.lang.Object") {
            return false
        }

        // Look for equals method defined directly in this class (not inherited from Object)
        val equalsMethods = psiClass.findMethodsByName("equals", false)
        for (method in equalsMethods) {
            val params = method.parameterList.parameters
            if (params.size == 1 && params[0].type.equalsToText("java.lang.Object")) {
                return true
            }
        }

        // Check superclasses (but not java.lang.Object)
        val superClass = psiClass.superClass
        if (superClass != null && superClass.qualifiedName != "java.lang.Object") {
            return hasCustomEquals(context, superClass)
        }

        // Check if it's a data class (Kotlin data classes auto-generate equals)
        if (isKotlinDataClass(psiClass)) {
            return true
        }

        return false
    }

    private fun isKotlinDataClass(psiClass: PsiClass): Boolean {
        // Kotlin data classes have component functions and equals generated
        val modifierList = psiClass.modifierList ?: return false
        val annotations = modifierList.annotations
        for (annotation in annotations) {
            val name = annotation.qualifiedName ?: continue
            if (name == "kotlin.Metadata") {
                // Check if it's a data class by looking for component1 method
                val componentMethod = psiClass.findMethodsByName("component1", false)
                if (componentMethod.isNotEmpty()) {
                    return true
                }
                // Also check for copy method which is unique to data classes
                val copyMethod = psiClass.findMethodsByName("copy", false)
                if (copyMethod.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }
}