package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastVisitor

class WrongConstructorDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor? {
        return object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                if (node.isConstructor) return false

                val containingClass = node.getContainingUClass() ?: return false
                val className = containingClass.name ?: return false
                if (node.name == className) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This method has the same name as the containing class but is not a constructor; did you accidentally add a return type?"
                    )
                }
                return false
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
            Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}