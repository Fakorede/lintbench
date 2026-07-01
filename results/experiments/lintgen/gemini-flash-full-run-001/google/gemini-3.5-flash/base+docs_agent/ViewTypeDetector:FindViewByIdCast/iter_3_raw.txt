package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // We only care about Java files, as Kotlin handles this differently
        if (isKotlin(context.psiFile)) {
            return
        }

        val parent = skipParenthesesParent(node)
        if (parent is UIfExpression && parent.sourcePsi is PsiConditionalExpression) {
            val ternaryParent = skipParenthesesParent(parent)
            if (ternaryParent is UBinaryExpressionWithType && ternaryParent.operationKind.name == "type_cast") {
                val castType = ternaryParent.type
                if (castType != null) {
                    val thenBranch = parent.thenExpression.unwrap()
                    val elseBranch = parent.elseExpression.unwrap()
                    val callUnwrapped = node.unwrap()

                    val other = if (thenBranch?.sourcePsi == callUnwrapped?.sourcePsi) {
                        elseBranch
                    } else if (elseBranch?.sourcePsi == callUnwrapped?.sourcePsi) {
                        thenBranch
                    } else {
                        null
                    }

                    if (other != null) {
                        val otherType = other.getExpressionType()
                        if (otherType != null && !castType.isAssignableFrom(otherType)) {
                            val castTypeString = ternaryParent.typeReference?.sourcePsi?.text ?: castType.presentableText
                            val message = "Add explicit cast to `findViewById` to avoid type inference issues in Java 8"
                            val sourceText = node.sourcePsi?.text ?: node.asSourceString()
                            val fix = fix()
                                .name("Add cast to $castTypeString")
                                .replace()
                                .range(context.getLocation(node))
                                .with("($castTypeString) $sourceText")
                                .build()

                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                message,
                                fix
                            )
                        }
                    }
                }
            }
        }
    }

    private fun skipParenthesesParent(element: UElement?): UElement? {
        var curr = element?.uastParent
        while (curr is UParenthesizedExpression) {
            curr = curr.uastParent
        }
        return curr
    }

    private fun UExpression?.unwrap(): UExpression? {
        var curr = this
        while (curr is UParenthesizedExpression) {
            curr = curr.expression
        }
        return curr
    }

    companion object {
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
}