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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        SuperCallHandler(context)

    private inner class SuperCallHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val receiver = node.receiver ?: return
            if (receiver !is USuperExpression) return

            val method = node.resolve() ?: return
            val modifierList = method.modifierList ?: return
            if (modifierList.findAnnotation(ANNOTATION_ANDROIDX) != null ||
                modifierList.findAnnotation(ANNOTATION_SUPPORT) != null
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                    "Overriding methods should not call a super implementation annotated with @EmptySuper"
                )
            }
        }
    }

    companion object {
        private const val ANNOTATION_ANDROIDX = "androidx.annotation.EmptySuper"
        private const val ANNOTATION_SUPPORT = "android.support.annotation.EmptySuper"

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper should not be invoked via super by overriding methods.
                The super implementation is either empty or contains code that should not run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}