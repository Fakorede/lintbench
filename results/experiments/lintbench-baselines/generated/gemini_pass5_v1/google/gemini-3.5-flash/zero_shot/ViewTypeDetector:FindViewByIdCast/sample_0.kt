package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("findViewById", "requireViewById")

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        if (context.file.extension == "kt" || context.file.extension == "kts") {
            return
        }

        val languageLevel = context.project.javaLanguageLevel
        if (languageLevel != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }

        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.view.View", false) &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Activity", false) &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Dialog", false) &&
            !evaluator.isMemberInSubClassOf(method, "android.view.Window", false)
        ) {
            return
        }

        var parent: UElement? = call.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }

        if (parent is UTypeCastExpression) {
            return
        }

        when (parent) {
            is UVariable -> {
                checkTypeAndReport(parent.type, call, context)
            }
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    val leftType = parent.leftOperand.getExpressionType()
                    if (leftType != null) {
                        checkTypeAndReport(leftType, call, context)
                    }
                }
            }
            is UReturnExpression -> {
                val containingMethod = parent.getParentOfType<UMethod>()
                val returnType = containingMethod?.returnType
                if (returnType != null) {
                    checkTypeAndReport(returnType, call, context)
                }
            }
        }
    }

    private fun checkTypeAndReport(type: PsiType, call: UCallExpression, context: JavaContext) {
        if (type is PsiClassType) {
            val evaluator = context.evaluator
            val psiClass = type.resolve()
            if (psiClass != null && evaluator.inheritsFrom(psiClass, "android.view.View", false)) {
                val fqName = psiClass.qualifiedName
                if (fqName != "android.view.View") {
                    val castType = type.presentableText
                    val source = call.sourcePsi?.text ?: return
                    val fix = fix()
                        .name("Add cast to $castType")
                        .replace()
                        .with("(($castType) $source)")
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        "Add explicit cast to `$castType`",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which  \
                means that most of the time you can leave out explicit casts and just assign  \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause  \
                code to not compile without explicit casts. This lint check looks for these  \
                scenarios and suggests casts to be added now such that the code will  \
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
}