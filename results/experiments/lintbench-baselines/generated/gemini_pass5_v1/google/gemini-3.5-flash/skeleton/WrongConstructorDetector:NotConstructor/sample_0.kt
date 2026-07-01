package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.SourceCodeScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
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
                This check catches methods that look like they were intended to be constructors, but aren't. \
                For example, in Java, if you declare a method with the same name as the class but you specify \
                a return type (such as void), then it is a method, not a constructor. If you intended to declare \
                a constructor, you should omit the return type.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) {
                    return
                }

                val containingClass = node.containingClass ?: return
                val className = containingClass.name ?: return

                if (node.name == className) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method `${node.name}` looks like a constructor but has a return type"
                    )
                }
            }
        }
}