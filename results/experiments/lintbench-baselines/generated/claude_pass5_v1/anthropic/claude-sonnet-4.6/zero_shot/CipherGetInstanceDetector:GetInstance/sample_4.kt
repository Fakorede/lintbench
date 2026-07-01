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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {

    companion object {
        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE = "getInstance"

        @JvmField
        val GET_INSTANCE_ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """,
            moreInfo = "https://goo.gle/GetInstance",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments - should report (though unusual for getInstance)
            context.report(
                GET_INSTANCE_ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode"
            )
            return
        }

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString() ?: return

        // Check if transformation string contains ECB or has no mode specified
        // A full transformation is "algorithm/mode/padding"
        // If there's no slash, only the algorithm is specified (defaults to ECB on Android)
        val parts = transformation.split("/")

        if (parts.size == 1) {
            // No mode specified - defaults to ECB on Android
            context.report(
                GET_INSTANCE_ISSUE,
                node,
                context.getLocation(firstArg as? ULiteralExpression ?: node),
                "Cipher.getInstance should not be called without setting the cipher mode " +
                    "because the default mode on Android is ECB, which is insecure"
            )
        } else if (parts.size >= 2) {
            val mode = parts[1].trim().uppercase()
            if (mode == "ECB") {
                context.report(
                    GET_INSTANCE_ISSUE,
                    node,
                    context.getLocation(firstArg as? ULiteralExpression ?: node),
                    "Cipher.getInstance should not be called with ECB as the cipher mode"
                )
            }
        }
    }
}