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
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/GetInstance"
        )

        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE = "getInstance"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.inheritsFrom(containingClass, CIPHER_CLASS, false)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString() ?: run {
            // If we can't evaluate the string, we can't determine if it's safe
            return
        }

        // Check if the transformation string is just an algorithm with no mode specified
        // e.g., "AES" instead of "AES/CBC/PKCS5Padding"
        val parts = transformation.split("/")

        if (parts.size == 1) {
            // No mode specified - default is ECB which is insecure
            context.report(
                ISSUE,
                node,
                context.getLocation(firstArg),
                "Cipher.getInstance should not be called without setting the encryption mode and padding"
            )
        } else if (parts.size >= 2) {
            val mode = parts[1].trim().uppercase()
            if (mode == "ECB") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(firstArg),
                    "Cipher.getInstance should not be called with ECB as the cipher mode"
                )
            }
        }
    }
}