package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.isConstructor) return
            if (node.sourcePsi !is PsiMethod) return

            val containingClass = node.containingClass ?: return
            val className = containingClass.name ?: return
            val methodName = node.name

            if (methodName == className) {
                context.report(
                    NOT_CONSTRUCTOR,
                    node,
                    context.getNameLocation(node),
                    "Method '$methodName' has the same name as the class but declares a return type, " +
                            "so it is not a constructor"
                )
            }
        }
    }

    companion object {
        @JvmField
        val NOT_CONSTRUCTOR = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = "A constructor must have the same name as its class and must not declare " +
                    "a return type. Methods that are named like the class but declare a return type " +
                    "look like intended constructors but will not be invoked as such.",
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