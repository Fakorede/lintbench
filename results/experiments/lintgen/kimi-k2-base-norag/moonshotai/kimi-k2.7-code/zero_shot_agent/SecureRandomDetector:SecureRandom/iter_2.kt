package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastVisitor

class SecureRandomDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val resolved = node.resolve() ?: return super.visitCallExpression(node)
                val containingClass = resolved.containingClass?.qualifiedName ?: return super.visitCallExpression(node)

                if (containingClass != "java.security.SecureRandom") {
                    return super.visitCallExpression(node)
                }

                if (resolved.isConstructor && node.valueArgumentCount > 0) {
                    report(context, node)
                } else if (resolved.name == "setSeed") {
                    report(context, node)
                }

                return super.visitCallExpression(node)
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using a fixed seed with SecureRandom can produce a predictable sequence of numbers"
        )
    }

    companion object {
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with SecureRandom",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable sequence of numbers.
                This may be useful for testing but it is not appropriate for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}