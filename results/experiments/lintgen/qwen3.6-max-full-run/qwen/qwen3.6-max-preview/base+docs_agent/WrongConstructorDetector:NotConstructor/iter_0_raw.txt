package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val cls = node.containingClass ?: return
                if (node.isConstructor) return
                if (node.name != cls.name) return

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "This method looks like a constructor but has a return type, so it's actually a regular method"
                )
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = "This check catches methods that look like they were intended to be constructors, but aren't. This usually happens when you accidentally add a return type (like `void`) to a constructor, which turns it into a regular method.",
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