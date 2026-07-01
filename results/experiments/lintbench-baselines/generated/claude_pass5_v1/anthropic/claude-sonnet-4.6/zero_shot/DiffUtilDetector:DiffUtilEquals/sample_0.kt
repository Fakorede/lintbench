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

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.Callback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods
        for (method in methods) {
            if (method.name == "areContentsTheSame") {
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
                        !leftType.equalsToText("short")
                    ) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Suspicious equality check: Did you mean `.equals()` instead of `===`?"
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
        val psiClass = context.evaluator.getTypeClass(receiverType) ?: return
        if (hasCustomEquals(context, psiClass)) {
            return
        }
        // Check if this is a class that hasn't overridden equals
        val className = psiClass.qualifiedName ?: psiClass.name ?: return
        // Skip known classes that implement equals properly
        if (isKnownEqualsClass(className)) {
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
        // Check if the class itself declares equals (not inherited from Object)
        val methods = psiClass.findMethodsByName("equals", false)
        if (methods.isNotEmpty()) {
            return true
        }

        // Check superclasses (but not Object)
        val superClass = psiClass.superClass ?: return false
        val superName = superClass.qualifiedName ?: return false
        if (superName == "java.lang.Object" || superName == "java.lang.Enum") {
            return false
        }

        return hasCustomEquals(context, superClass)
    }

    private fun isKnownEqualsClass(className: String): Boolean {
        return className.startsWith("java.lang.") ||
            className.startsWith("java.util.") ||
            className.startsWith("kotlin.") ||
            className.startsWith("android.") ||
            className == "java.math.BigDecimal" ||
            className == "java.math.BigInteger"
    }

    companion object {
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
}