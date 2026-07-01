package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class ViewTypeDetector : Detector(), SourceCodeScanner {

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

    override fun getApplicableMethodNames(): List<String>? = listOf("findViewById", "requireViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val params = method.parameterList.parameters
        if (params.size != 1 || params[0].type != PsiType.INT) return

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }

        if (parent is UCastExpression) return

        if (parent is UQualifiedReferenceExpression) {
            var receiver: UExpression? = parent.receiver
            while (receiver is UParenthesizedExpression) {
                receiver = receiver.expression
            }
            if (receiver != node) return

            val resolved = parent.resolve()
            if (resolved is PsiMember) {
                val containingClass = resolved.containingClass
                if (containingClass != null) {
                    if (!context.evaluator.extendsClass(containingClass, "android.view.View", false)) {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Add explicit cast here to ensure Java 8 compatibility"
                        )
                    }
                }
            }
        }
    }
}