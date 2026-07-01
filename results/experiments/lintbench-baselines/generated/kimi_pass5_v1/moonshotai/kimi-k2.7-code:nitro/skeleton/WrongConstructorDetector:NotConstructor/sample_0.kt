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
            explanation = "This method has the same name as its containing class. " +
                "A method whose name matches the class name and has no return type is " +
                "treated as a constructor. If this method declares a return type, it is " +
                "not a constructor, which usually indicates that the author intended it " +
                "to be one. Remove the return type if it should be a constructor, or " +
                "rename the method to avoid confusion.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (looksLikeConstructor(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This method has the same name as its class and looks like a constructor, but it has a return type"
                    )
                }
            }
        }

    private fun looksLikeConstructor(method: UMethod): Boolean {
        if (method.isConstructor) {
            return false
        }
        val methodName = method.name ?: return false
        val className = method.containingClass?.name ?: return false
        return methodName == className
    }
}