package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElementHandler

class SecureRandomDetector : Detector(), UastScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                if (method.containingClass?.qualifiedName != "java.security.SecureRandom") return

                val params = method.parameterList.parameters
                if (params.isEmpty()) return

                val paramType = params[0].type?.canonicalText
                val isConstructor = node.isConstructor()
                val isSetSeed = node.methodName == "setSeed"

                val isSeedApi = (isConstructor && paramType == "byte[]") ||
                                (isSetSeed && (paramType == "byte[]" || paramType == "long"))

                if (isSeedApi) {
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

    companion object {
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with SecureRandom",
            explanation = "Specifying a fixed seed will cause the instance to return a predictable sequence of numbers. This may be useful for testing but it is not appropriate for secure use.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/SecureRandom"
        )
    }
}