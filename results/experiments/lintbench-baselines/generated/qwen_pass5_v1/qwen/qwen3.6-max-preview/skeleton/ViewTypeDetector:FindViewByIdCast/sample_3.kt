package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = "In Android O, the `findViewById` signature switched to using generics, which means that most of the time you can leave out explicit casts and just assign the result of the `findViewById` call to variables of specific view classes. However, due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts. This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No XML attributes to inspect for this issue
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("findViewById")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        // Skip parentheses to find the real parent
        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }

        // If already explicitly cast, no need to warn
        if (parent is UCastExpression) {
            return
        }

        // Java 8 type inference may fail or infer View/Object incorrectly when
        // findViewById is chained, passed as an argument, or returned without a cast.
        val needsCast = when (parent) {
            is UQualifiedReferenceExpression -> parent.receiver == node
            is UCallExpression -> node in parent.valueArguments
            is UReturnExpression -> true
            else -> false
        }

        if (needsCast) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to `findViewById()` to ensure Java 8 compatibility"
            )
        }
    }
}