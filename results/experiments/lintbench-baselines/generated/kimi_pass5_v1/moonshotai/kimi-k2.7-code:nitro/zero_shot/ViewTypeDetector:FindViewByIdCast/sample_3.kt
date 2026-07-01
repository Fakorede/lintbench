package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable

private const val VIEW_CLASS = "android.view.View"

class ViewTypeDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isFindViewByIdCall) return
                if (node.isAlreadyCast) return
                if (node.isDirectAssignment) return

                val expectedType = node.getExpectedType() ?: return
                val expectedClass = context.evaluator.getTypeClass(expectedType) ?: return

                if (expectedClass.qualifiedName == VIEW_CLASS) return
                if (!context.evaluator.extendsClass(expectedClass, VIEW_CLASS, false)) return

                val target = expectedClass.name ?: "View"
                val message = "Add explicit cast to $target around this findViewById call"
                context.report(ISSUE, node, context.getLocation(node), message)
            }
        }

    private val UCallExpression.isFindViewByIdCall: Boolean
        get() = methodName == "findViewById" && typeArguments.isEmpty() && valueArgumentCount == 1

    private val UCallExpression.isAlreadyCast: Boolean
        get() = uastParent is UTypeCastExpression

    private val UCallExpression.isDirectAssignment: Boolean
        get() {
            val parent = uastParent ?: return false
            return parent is UVariable || parent is UBinaryExpression
        }

    companion object {
        private const val ID = "FindViewByIdCast"
        private val ISSUE = Issue.create(
            id = ID,
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which
                means that most of the time you can leave out explicit casts and just assign
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause
                code to not compile without explicit casts. This lint check looks for these
                scenarios and suggests casts to be added now such that the code will continue
                to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}