package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (!qualifiedName.startsWith("java.security.") && !qualifiedName.startsWith("javax.crypto.")) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.size < 2) return

        val providerArgument: UExpression = arguments[1]
        val providerValue = context.evaluate(providerArgument) as? String

        if (providerValue == "BC") {
            context.report(
                ISSUE,
                context.getLocation(providerArgument),
                "Using BC Provider"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.\n\n" +
                    "Reference documentation:\n" +
                    "- https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html\n" +
                    "- https://goo.gle/DeprecatedProvider",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}