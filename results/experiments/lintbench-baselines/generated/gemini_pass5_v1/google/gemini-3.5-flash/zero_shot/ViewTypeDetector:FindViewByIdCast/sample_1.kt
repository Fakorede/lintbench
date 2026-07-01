package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById", "findViewWithTag", "requireViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val languageLevel = context.project.sourceLanguageLevel
        if (languageLevel != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }

        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!isViewContainer(qualifiedName)) {
            return
        }

        var curr = node.uastParent
        while (curr != null) {
            if (curr is UTypeCastExpression) {
                return
            }
            if (curr is UCallExpression) {
                if (curr.valueArguments.any { isOrContains(it, node) }) {
                    val outerMethod = curr.resolve()
                    if (outerMethod != null) {
                        val hasTypeParams = outerMethod.hasTypeParameters() || 
                                (outerMethod.containingClass?.hasTypeParameters() == true)
                        val hasExplicitTypeArguments = curr.typeArguments.isNotEmpty()
                        if (hasTypeParams && !hasExplicitTypeArguments) {
                            val castType = "View"
                            val fix = fix()
                                .name("Add explicit cast to $castType")
                                .replace()
                                .range(context.getLocation(node))
                                .beginning()
                                .with("($castType) ")
                                .build()

                            context.report(
                                ISSUE_CAST,
                                node,
                                context.getLocation(node),
                                "Add explicit cast here to avoid compile-time errors in Java 8",
                                fix
                            )
                        }
                    }
                }
                break
            }
            if (curr is ULocalVariable || curr is UBinaryExpression || curr is UReturnExpression) {
                break
            }
            curr = curr.uastParent
        }
    }

    private fun isViewContainer(qualifiedName: String): Boolean {
        return qualifiedName.startsWith("android.view.") ||
                qualifiedName.startsWith("android.app.") ||
                qualifiedName.startsWith("androidx.fragment.app.") ||
                qualifiedName.startsWith("android.support.v4.app.")
    }

    private fun isOrContains(expression: UExpression?, target: UExpression): Boolean {
        var expr = expression
        while (expr is UParenthesizedExpression) {
            expr = expr.expression
        }
        return expr == target
    }

    companion object {
        @JvmField
        val ISSUE_CAST = Issue.create(
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