package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val methodName = node.name ?: return
                var parent = node.uastParent
                while (parent != null && parent !is UClass) {
                    parent = parent.uastParent
                }

                if (parent is UClass && parent.name == methodName) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Method '$methodName' is named like its surrounding class but is not a " +
                            "constructor; did you mean to declare a constructor instead?"
                    )
                }
            }
        }

    companion object {
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = "This check catches methods that look like they were intended to be constructors, but aren't.",
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