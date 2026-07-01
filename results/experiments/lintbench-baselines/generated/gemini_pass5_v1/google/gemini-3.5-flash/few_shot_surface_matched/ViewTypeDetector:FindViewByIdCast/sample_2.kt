package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    private val idToView = HashMap<String, String>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("id")
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value
        val id = when {
            value.startsWith("@+id/") -> value.substring("@+id/".length)
            value.startsWith("@id/") -> value.substring("@id/".length)
            else -> null
        }
        if (id != null) {
            val viewType = attribute.ownerElement.tagName
            idToView[id] = viewType
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name != "findViewById") return

        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.view.View") &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
            !evaluator.isMemberInSubClassOf(method, "android.app.Dialog") &&
            !evaluator.isMemberInSubClassOf(method, "android.view.Window")
        ) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val idName = getIdName(argument) ?: return
        val xmlType = idToView[idName] ?: return
        if (xmlType == "View" || xmlType == "android.view.View") return

        val cast = getSurroundingCast(node)
        val isJava7 = context.project.javaLanguageLevel?.let {
            !it.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)
        } ?: false

        if (cast == null && isJava7) {
            var parent = node.uastParent
            while (parent is UParenthesizedExpression) {
                parent = parent.uastParent
            }
            val isAssignment = parent is UBinaryExpression &&
                (parent.operator == UastBinaryOperator.ASSIGN ||
                 parent.operator.text == "=")
            val isVariableDecl = parent is UVariable

            if (!isAssignment && !isVariableDecl) {
                val message = "Add explicit cast to $xmlType"
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

    private fun getIdName(argument: UExpression): String? {
        val s = argument.asSourceString()
        val lastDot = s.lastIndexOf('.')
        if (lastDot != -1) {
            return s.substring(lastDot + 1).trim()
        }
        return null
    }

    private fun getSurroundingCast(node: UExpression): UCastExpression? {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UCastExpression) {
                return current
            }
            if (current is UParenthesizedExpression) {
                current = current.uastParent
            } else {
                break
            }
        }
        return null
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }
}