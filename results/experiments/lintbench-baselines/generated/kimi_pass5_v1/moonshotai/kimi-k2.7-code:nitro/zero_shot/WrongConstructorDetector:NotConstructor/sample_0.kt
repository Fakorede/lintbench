package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes() = listOf(UMethod::class.java)

    override fun visitMethod(context: JavaContext, node: UMethod) {
        if (node.isConstructor) {
            return
        }

        val methodName = node.name ?: return
        val className = node.containingClass?.name ?: return

        if (methodName == className) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "This method looks like a constructor but is not; it declares a return type."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This method has the same name as the surrounding class and appears to have been \
                intended as a constructor. However, because it declares a return type it is a \
                normal method, not a constructor, and will not be invoked during object construction.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}