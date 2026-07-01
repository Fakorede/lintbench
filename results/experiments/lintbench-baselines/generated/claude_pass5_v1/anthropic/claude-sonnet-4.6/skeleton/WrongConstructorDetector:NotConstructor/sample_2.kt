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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongConstructorDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors, but aren't.

                If you have a method which has the same name as the containing class, \
                it is possible that you have accidentally named a constructor as a method \
                (by including a return type).
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Constructors are not flagged — we only care about regular methods
                if (node.isConstructor) return

                // Get the method name
                val methodName = node.name

                // Get the containing class
                val containingClass = node.containingClass ?: return

                // Get the containing class name
                val className = containingClass.name ?: return

                // If the method name matches the class name, it looks like an intended constructor
                if (methodName == className) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getNameLocation(node),
                        message = "Method `$methodName` looks like a constructor but has a return type; should it be a constructor?"
                    )
                }
            }
        }
}