package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val containingClass = node.getParentOfType(UClass::class.java, true) ?: return
                if (node.name != containingClass.name) return

                val message =
                    "This method has the same name as the class and looks like a constructor, but has a return type."
                context.report(ISSUE, node, context.getNameLocation(node), message)
            }
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongConstructorDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors,
                but aren't. A method that has the same name as the surrounding class but declares
                a return type is a normal method, not a constructor.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}