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
                val psiMethod = node.javaPsi
                if (psiMethod.isConstructor) {
                    return
                }

                val containingClass = psiMethod.containingClass ?: return
                val className = containingClass.name ?: return
                val methodName = psiMethod.name

                if (methodName == className) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method `$methodName` looks like a constructor but has a return type"
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
            explanation = """
                This check catches methods that look like they were intended to be constructors, but aren't.
                
                In Java, a constructor must not have a return type. If you accidentally add a return type \
                (even `void`) to a constructor, it becomes a regular method with the same name as the class, \
                which is confusing and usually a bug.
                """.trimIndent(),
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