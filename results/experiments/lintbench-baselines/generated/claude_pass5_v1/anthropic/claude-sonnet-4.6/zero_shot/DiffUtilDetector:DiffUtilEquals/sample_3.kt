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
        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"

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
            moreInfo = "https://issuetracker.google.com/116789824",
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
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
                    val leftType = node.leftOperand.getExpressionType()
                    if (leftType != null && !leftType.equalsToText("boolean") &&
                        !leftType.equalsToText("int") &&
                        !leftType.equalsToText("long") &&
                        !leftType.equalsToText("double") &&
                        !leftType.equalsToText("float") &&
                        !leftType.equalsToText("char") &&
                        !leftType.equalsToText("byte") &&
                        !leftType.equalsToText("short") &&
                        !isPrimitiveOrBoxed(leftType)
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
                    val method = node.resolve()
                    if (method != null) {
                        val containingClass = method.containingClass
                        if (containingClass != null && !hasCustomEquals(containingClass, context)) {
                            val receiverType = node.receiverType
                            if (receiverType != null && !isPrimitiveOrBoxed(receiverType)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Suspicious equality check: `equals()` is not implemented in `${containingClass.name}`"
                                )
                            }
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun isPrimitiveOrBoxed(type: PsiType): Boolean {
        val canonicalText = type.canonicalText
        return when (canonicalText) {
            "boolean", "int", "long", "double", "float", "char", "byte", "short",
            "java.lang.Boolean", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Character",
            "java.lang.Byte", "java.lang.Short", "java.lang.String",
            "java.lang.Number" -> true
            else -> false
        }
    }

    private fun hasCustomEquals(psiClass: PsiClass, context: JavaContext): Boolean {
        // Check if the class itself (not inherited from Object) defines equals
        for (method in psiClass.methods) {
            if (isEqualsMethod(method)) {
                return true
            }
        }

        // Check superclasses (but not java.lang.Object)
        val superClass = psiClass.superClass ?: return false
        val superClassName = superClass.qualifiedName ?: return false
        if (superClassName == "java.lang.Object") {
            return false
        }

        return hasCustomEquals(superClass, context)
    }

    private fun isEqualsMethod(method: PsiMethod): Boolean {
        if (method.name != "equals") return false
        val parameters = method.parameterList.parameters
        if (parameters.size != 1) return false
        val paramType = parameters[0].type
        return paramType.equalsToText("java.lang.Object") || paramType.canonicalText == "Object"
    }
}