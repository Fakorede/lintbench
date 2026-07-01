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
            explanation = "This check catches methods that look like they were intended to be " +
                "constructors, but aren't.",
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
                // Skip actual constructors
                if (node.isConstructor) return

                // Get the containing class
                val containingClass = node.javaPsi.containingClass ?: return
                val className = containingClass.name ?: return
                val methodName = node.name

                // Check if the method name matches the containing class name
                // (which would make it look like a constructor, but it has a return type)
                if (methodName == className) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method $methodName looks like a constructor but has a return type; " +
                            "did you mean to be a constructor?",
                    )
                }
            }
        }
}