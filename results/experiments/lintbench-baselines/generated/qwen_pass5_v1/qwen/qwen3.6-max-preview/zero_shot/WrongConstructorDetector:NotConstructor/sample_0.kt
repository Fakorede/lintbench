package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType

class WrongConstructorDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(): UElementHandler = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.isConstructor) return

            val containingClass = node.getUastParentOfType<UClass>() ?: return
            if (node.name == containingClass.name) {
                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "This method has the same name as the containing class but declares a return type, so it is not a constructor. Remove the return type if you intended this to be a constructor."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = "This check catches methods that look like they were intended to be constructors, but aren't. This typically happens when a return type (such as `void`) is accidentally specified for a constructor, which turns it into a regular method.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}