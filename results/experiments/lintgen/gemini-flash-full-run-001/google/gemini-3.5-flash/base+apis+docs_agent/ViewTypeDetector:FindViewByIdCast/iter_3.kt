package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CLASS_VIEW = "android.view.View"

        @JvmField
        val ISSUE = Issue.create(
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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById", "findViewWithTag", "requireViewById")
    }

    private fun skipParenthesizedExprUp(element: UElement?): UElement? {
        var current = element
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }

    private fun skipParenthesizedExprDown(expression: UExpression?): UExpression? {
        var current = expression
        while (current is UParenthesizedExpression) {
            current = current.expression
        }
        return current
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.isKotlin) {
            return
        }

        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator
        if (!evaluator.inheritsFrom(containingClass, CLASS_VIEW, false) &&
            !evaluator.inheritsFrom(containingClass, "android.app.Activity", false) &&
            !evaluator.inheritsFrom(containingClass, "android.app.Dialog", false) &&
            !evaluator.inheritsFrom(containingClass, "android.view.Window", false)
        ) {
            return
        }

        val parent = skipParenthesizedExprUp(node.uastParent)
        if (parent is UQualifiedReferenceExpression) {
            if (skipParenthesizedExprDown(parent.receiver) == node) {
                val selector = parent.selector
                if (selector is UCallExpression) {
                    val resolved = selector.resolve()
                    if (resolved != null) {
                        val methodClass = resolved.containingClass
                        if (methodClass != null) {
                            val qualifiedName = methodClass.qualifiedName
                            if (qualifiedName != null &&
                                qualifiedName != CLASS_VIEW &&
                                qualifiedName != "java.lang.Object"
                            ) {
                                val message = "Add explicit cast here; backporting `findViewById` to return `<T extends View>` " +
                                        "will cause this expression to fail compilation in Java 8 without a cast"
                                context.report(ISSUE, node, context.getLocation(node), message)
                            }
                        }
                    }
                }
            }
        } else if (parent is UCallExpression) {
            val resolved = parent.resolve()
            if (resolved != null) {
                val name = resolved.name
                val containing = resolved.containingClass
                if (containing != null) {
                    val methods = containing.findMethodsByName(name, true)
                    if (methods.size > 1) {
                        val message = "Add explicit cast here; backporting `findViewById` to return `<T extends View>` " +
                                "will cause this expression to fail compilation in Java 8 without a cast"
                        context.report(ISSUE, node, context.getLocation(node), message)
                    }
                }
            }
        }
    }
}