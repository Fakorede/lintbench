package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableCallNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, parent: UElement?) {
        val resolve = node.resolve() ?: return
        val containingClass = resolve.containingClass?.qualifiedName ?: return
        if (containingClass != "javax.crypto.Cipher") {
            return
        }

        val args = node.valueArguments
        if (args.size == 2) {
            val provider = args[1]
            if (provider is ULiteralExpression && provider.value == "BC") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The BC provider has been deprecated and will not be provided when targetSdkVersion is P or higher."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when
                targetSdkVersion is P or higher. Use a different provider, or rely on the
                default provider instead.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}