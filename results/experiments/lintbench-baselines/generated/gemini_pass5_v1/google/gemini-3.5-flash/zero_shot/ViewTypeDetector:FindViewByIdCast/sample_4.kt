package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isJava
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UParenthesizedExpression

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val psi = node.sourcePsi ?: return
        if (!isJava(psi)) {
            return
        }
        checkFindViewByIdCast(context, node)
    }

    private fun checkFindViewByIdCast(context: JavaContext, node: UCallExpression) {
        var parent = node.uastParent
        while (parent != null && parent !is UCastExpression) {
            if (parent !is UParenthesizedExpression) {
                break
            }
            parent = parent.uastParent
        }
        val cast = parent as? UCastExpression ?: return
        val castType = cast.type ?: return
        if (castType is PsiClassType) {
            val cls = castType.resolve() ?: return
            if (cls.isInterface) {
                // Check if there's already an intermediate cast to View
                val operand = cast.operand
                if (operand is UCastExpression) {
                    val operandType = operand.type
                    if (operandType != null && operandType.canonicalText == "android.view.View") {
                        return
                    }
                }

                val message = "Cast of `findViewById` to `${cls.name}` will cause a compilation error in Java 8 unless cast to `View` first"
                val sourceText = node.sourcePsi?.text ?: node.asSourceString()
                val fix = fix()
                    .name("Cast to View first")
                    .replace()
                    .range(context.getLocation(node))
                    .with("(android.view.View) $sourceText")
                    .autoFix()
                    .build()

                context.report(
                    FIND_VIEW_BY_ID_CAST,
                    cast,
                    context.getLocation(cast),
                    message,
                    fix
                )
            }
        }
    }

    companion object {
        @JvmField
        val FIND_VIEW_BY_ID_CAST = Issue.create(
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
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARN,
            androidSpecific = true,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}