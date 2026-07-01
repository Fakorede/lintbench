package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass

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
                "constructors, but aren't. A method that has the same name as the containing " +
                "class but has a return type is not a constructor; it is a plain method. This " +
                "is usually a mistake.",
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

                // Get the method name
                val methodName = node.name

                // Get the containing class name
                val containingClass = node.getContainingUClass() ?: return
                val className = containingClass.name ?: return

                // If the method name matches the class name, it looks like a constructor
                // but has a return type, so it's not actually a constructor
                if (methodName == className) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getNameLocation(node),
                        message = "Method `$methodName` looks like a constructor but is not a constructor; " +
                            "it has a return type. If this is meant to be a constructor, remove the return type.",
                    )
                }
            }
        }
}