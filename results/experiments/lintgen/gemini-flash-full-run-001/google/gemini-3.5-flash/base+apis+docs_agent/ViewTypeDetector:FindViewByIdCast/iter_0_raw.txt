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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CLASS_VIEW = "android.view.View"
        private const val CLASS_OBJECT = "java.lang.Object"

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

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.file.extension.equals("kt", ignoreCase = true)) {
            return
        }

        val languageLevel = context.project.languageLevel
        if (languageLevel != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }

        val containingClass = method.containingClass ?: return
        if (!InheritanceUtil.isInheritor(containingClass, CLASS_VIEW) &&
            containingClass.qualifiedName != "android.app.Activity" &&
            containingClass.qualifiedName != "android.app.Dialog" &&
            containingClass.qualifiedName != "android.view.Window"
        ) {
            return
        }

        val parent = skipParenthesesUp(node.uastParent) ?: return
        if (parent is UQualifiedReferenceExpression) {
            if (skipParenthesesDown(parent.receiver) == node) {
                val selector = parent.selector
                if (selector is UCallExpression) {
                    val resolved = selector.resolve()
                    if (resolved != null) {
                        val methodClass = resolved.containingClass
                        if (methodClass != null) {
                            val qualifiedName = methodClass.qualifiedName
                            if (qualifiedName != null &&
                                qualifiedName != CLASS_VIEW &&
                                qualifiedName != CLASS_OBJECT
                            ) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Add explicit cast here; backporting `findViewById` to return `<T extends View>` " +
                                            "will cause this expression to fail compilation in Java 8 without a cast"
                                )
                            }
                        }
                    }
                }
            }
        } else if (parent is UCallExpression) {
            val resolved = parent.resolve()
            if (resolved != null) {
                val parameters = resolved.parameterList.parameters
                if (parameters.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast here; backporting `findViewById` to return `<T extends View>` " +
                                "will cause this expression to fail compilation in Java 8 without a cast"
                    )
                }
            }
        }
    }

    private fun skipParenthesesUp(element: UElement?): UElement? {
        var curr = element
        while (curr is UParenthesizedExpression) {
            curr = curr.uastParent
        }
        return curr
    }

    private fun skipParenthesesDown(element: UExpression?): UExpression? {
        var curr = element
        while (curr is UParenthesizedExpression) {
            curr = curr.expression
        }
        return curr
    }
}