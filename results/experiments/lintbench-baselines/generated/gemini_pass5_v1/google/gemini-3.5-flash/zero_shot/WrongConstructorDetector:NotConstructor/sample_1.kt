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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        MethodVisitor(context)

    private class MethodVisitor(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.isConstructor) {
                return
            }
            val containingClass = node.containingClass ?: return
            if (containingClass.isInterface) {
                return
            }
            val className = containingClass.name ?: return
            if (node.name == className) {
                val message = "Method `$className` looks like a constructor but has a return type"
                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    message
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors, \
                but aren't. For example, in Java, declaring a return type (such as `void`) \
                for a method with the same name as the containing class turns it into a \
                regular method rather than a constructor.
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