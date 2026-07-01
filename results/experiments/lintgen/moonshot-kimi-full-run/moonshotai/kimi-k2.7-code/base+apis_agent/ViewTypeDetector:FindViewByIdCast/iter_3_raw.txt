package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAssignmentExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Node

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which
                means that most of the time you can leave out explicit casts and just assign
                the result of the `findViewById` call to variables of specific view classes.
                However, due to language changes between Java 7 and 8, this change may cause
                code to not compile without explicit casts. This check flags those scenarios
                and suggests adding an explicit cast so the code continues to compile when the
                language level is updated to 1.8.
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

    override fun getApplicableMethodNames(): List<String> = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.file.name.endsWith(".java")) {
            return
        }

        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        if (!evaluator.extendsClass(containingClass, ANDROID_VIEW_VIEW, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_APP_ACTIVITY, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_APP_FRAGMENT, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_SUPPORT_V4_FRAGMENT, false) &&
            !evaluator.extendsClass(containingClass, ANDROIDX_FRAGMENT, false)
        ) {
            return
        }

        if (node.typeArguments.isNotEmpty()) return
        if (isAlreadyCast(node)) return

        val expectedType = getExpectedType(node) ?: return
        if (PsiType.VOID == expectedType) return

        val expectedClassType = expectedType as? PsiClassType ?: return
        if (ANDROID_VIEW_VIEW == expectedClassType.canonicalText) return

        val expectedClass = expectedClassType.resolve() ?: return
        if (!evaluator.extendsClass(expectedClass, ANDROID_VIEW_VIEW, false)) return

        val cast = expectedType.presentableText
        val original = node.asSourceString()
        val fix = LintFix.create()
            .name("Cast to $cast")
            .replace()
            .range(context.getLocation(node))
            .with("($cast) $original")
            .build()

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast to $cast",
            fix
        )
    }

    private fun isAlreadyCast(node: UExpression): Boolean {
        var current: UElement = node
        while (true) {
            val parent = current.uastParent ?: return false
            when (parent) {
                is UParenthesizedExpression,
                is UQualifiedReferenceExpression -> current = parent
                is UBinaryExpressionWithType -> {
                    return parent.operationKind == UastBinaryOperator.AS ||
                            parent.operationKind == UastBinaryOperator.SAFE_AS
                }
                is UTypeCastExpression -> return true
                else -> return false
            }
        }
    }

    private fun getExpectedType(node: UExpression): PsiType? {
        var current: UElement = node
        while (true) {
            val parent = current.uastParent ?: return null
            when (parent) {
                is UParenthesizedExpression,
                is UQualifiedReferenceExpression -> current = parent
                is UBinaryExpressionWithType,
                is UTypeCastExpression -> return null
                is UAssignmentExpression -> {
                    if (parent.operator == UastBinaryOperator.ASSIGN &&
                        parent.rightOperand === current
                    ) {
                        return parent.leftOperand.getExpressionType()
                    }
                    return null
                }
                is UBinaryExpression -> {
                    if (parent.operator == UastBinaryOperator.ASSIGN &&
                        parent.rightOperand === current
                    ) {
                        return parent.leftOperand.getExpressionType()
                    }
                    return null
                }
                is UVariable -> {
                    return if (parent.uastInitializer === current) parent.type else null
                }
                else -> current = parent
            }
        }
    }

    private companion object {
        const val FIND_VIEW_BY_ID = "findViewById"
        const val ANDROID_VIEW_VIEW = "android.view.View"
        const val ANDROID_APP_ACTIVITY = "android.app.Activity"
        const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        const val ANDROID_SUPPORT_V4_FRAGMENT = "android.support.v4.app.Fragment"
        const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"
    }
}