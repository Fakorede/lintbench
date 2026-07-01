package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
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
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
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

        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val ANDROID_VIEW_VIEW = "android.view.View"
        private const val ANDROID_APP_ACTIVITY = "android.app.Activity"
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val ANDROID_SUPPORT_V4_FRAGMENT = "android.support.v4.app.Fragment"
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"
        private const val ANDROID_VIEW_WINDOW = "android.view.Window"
        private const val ANDROID_APP_DIALOG = "android.app.Dialog"
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

        if (node.typeArguments.isNotEmpty()) return
        if (isAlreadyCast(node)) return

        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        if (!evaluator.extendsClass(containingClass, ANDROID_VIEW_VIEW, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_APP_ACTIVITY, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_APP_FRAGMENT, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_SUPPORT_V4_FRAGMENT, false) &&
            !evaluator.extendsClass(containingClass, ANDROIDX_FRAGMENT, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_VIEW_WINDOW, false) &&
            !evaluator.extendsClass(containingClass, ANDROID_APP_DIALOG, false)
        ) {
            return
        }

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
        var current: UExpression = node
        while (true) {
            val parent = current.uastParent ?: return false
            when (parent) {
                is UParenthesizedExpression,
                is UQualifiedReferenceExpression -> current = parent as UExpression
                is UBinaryExpressionWithType -> {
                    return parent.operationKind.name == "AS" ||
                            parent.operationKind.name == "SAFE_AS"
                }
                else -> return false
            }
        }
    }

    private fun getExpectedType(node: UExpression): PsiType? {
        var current: UExpression = node
        while (true) {
            val parent = current.uastParent ?: return null
            when (parent) {
                is UParenthesizedExpression,
                is UQualifiedReferenceExpression -> current = parent as UExpression
                is UBinaryExpressionWithType -> return null
                is UBinaryExpression -> {
                    if (parent.operator.name == "ASSIGN" && parent.rightOperand === current) {
                        return parent.leftOperand.getExpressionType()
                    }
                    return null
                }
                is UVariable -> {
                    return if (parent.uastInitializer === current) parent.type else null
                }
                is UCallExpression -> {
                    if (parent.receiver === current) {
                        current = parent
                        continue
                    }
                    val parameter = parent.getParameterForArgument(current) ?: return null
                    return parameter.type
                }
                is UReturnExpression -> {
                    var ancestor = parent.uastParent
                    while (ancestor != null) {
                        if (ancestor is UMethod) return ancestor.returnType
                        ancestor = ancestor.uastParent
                    }
                    return null
                }
                else -> current = parent as UExpression
            }
        }
    }
}