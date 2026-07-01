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
                val containingClass = node.containingClass ?: return
                if (node.name == containingClass.name && !node.isConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This method looks like a constructor but is not a constructor"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = "This check catches methods that look like they were intended to be constructors, but aren't.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}