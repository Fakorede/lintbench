package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/SecureRandom"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                val containingClass = method.containingClass ?: return
                if (containingClass.qualifiedName != "java.security.SecureRandom") {
                    return
                }

                val isVulnerable = if (method.isConstructor) {
                    node.valueArguments.isNotEmpty()
                } else {
                    method.name == "setSeed"
                }

                if (isVulnerable) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not use a fixed seed with SecureRandom"
                    )
                }
            }
        }
    }
}