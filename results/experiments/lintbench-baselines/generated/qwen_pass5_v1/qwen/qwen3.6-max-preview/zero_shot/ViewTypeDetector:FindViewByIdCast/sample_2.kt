package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), UastScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val cls = method.containingClass ?: return

        val isTargetClass = evaluator.isSubclassOf(cls, "android.app.Activity") ||
                evaluator.isSubclassOf(cls, "android.view.View") ||
                evaluator.isSubclassOf(cls, "android.app.Dialog") ||
                evaluator.isSubclassOf(cls, "android.app.Fragment") ||
                cls.qualifiedName == "androidx.fragment.app.Fragment"

        if (!isTargetClass) return

        // Only applies to the generic version of findViewById introduced in API 26
        if (method.typeParameters.isEmpty()) return

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent == null) return

        // If already explicitly cast, skip
        if (parent is UCastExpression || parent is UastBinaryExpressionWithType) return

        var shouldReport = false

        when (parent) {
            is UQualifiedReferenceExpression -> {
                val selector = parent.selector
                if (selector is UCallExpression) {
                    val resolvedMethod = selector.resolve()
                    if (resolvedMethod != null) {
                        val methodClass = resolvedMethod.containingClass
                        if (methodClass != null && !evaluator.isSubclassOf(methodClass, "android.view.View")) {
                            shouldReport = true
                        }
                    }
                }
            }
            is UVariable -> {
                val varType = parent.type
                if (varType != null && isViewOrObject(evaluator, varType)) {
                    shouldReport = true
                }
            }
            is UAssignmentExpression -> {
                val lhsType = parent.left.getExpressionType()
                if (lhsType != null && isViewOrObject(evaluator, lhsType)) {
                    shouldReport = true
                }
            }
            is UCallExpression -> {
                val argIndex = parent.valueArguments.indexOf(node)
                if (argIndex != -1) {
                    val resolvedCall = parent.resolve()
                    if (resolvedCall != null) {
                        val params = resolvedCall.parameterList.parameters
                        if (argIndex < params.size) {
                            val paramType = params[argIndex].type
                            if (isViewOrObject(evaluator, paramType)) {
                                shouldReport = true
                            }
                        }
                    }
                }
            }
        }

        if (shouldReport) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast"
            )
        }
    }

    private fun isViewOrObject(evaluator: JavaEvaluator, type: PsiType): Boolean {
        return evaluator.typeMatches(type, "android.view.View") ||
                evaluator.typeMatches(type, "java.lang.Object")
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}