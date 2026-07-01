package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingUClass
import java.util.EnumSet

class WrongConstructorDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val containingClass = node.getContainingUClass() ?: return
                if (node.name == containingClass.name) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This method looks like a constructor but has a return type, making it a regular method"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "NotConstructor",
            "Not a Constructor",
            "This check catches methods that look like they were intended to be constructors, but aren't.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(WrongConstructorDetector::class.java, EnumSet.of(Scope.JAVA_FILE_SCOPE))
        )
    }
}