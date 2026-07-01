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
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) {
                    return
                }
                val psiMethod = node.javaPsi
                val containingClass = psiMethod.containingClass ?: return
                val className = containingClass.name ?: return

                if (node.name == className) {
                    val message = "Method `${node.name}` has the same name as its containing class, but specifies a return type. " +
                            "It will be treated as a normal method, not a constructor. " +
                            "Did you mean to remove the return type?"
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        message
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Method name same as class name",
            explanation = """
                A method named the same as its class but with a return type (even `void`) \
                is not a constructor. It is a normal method. This is usually a typo where \
                the constructor return type was accidentally specified.
            """,
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