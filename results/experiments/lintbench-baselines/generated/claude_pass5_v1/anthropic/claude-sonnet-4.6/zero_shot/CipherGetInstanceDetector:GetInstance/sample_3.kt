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
        @JvmField
        val GET_INSTANCE_ISSUE: Issue = Issue.create(
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
            moreInfo = "https://goo.gle/GetInstance",
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE_METHOD = "getInstance"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != CIPHER_CLASS) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        val transformation = firstArg.evaluateString() ?: return

        // Check if the transformation string contains a cipher mode
        // A full transformation is of the form "algorithm/mode/padding"
        val parts = transformation.split("/")

        if (parts.size == 1) {
            // No mode specified - default is ECB on Android
            context.report(
                GET_INSTANCE_ISSUE,
                node,
                context.getLocation(firstArg),
                "Cipher.getInstance should not be called without setting the cipher mode and padding: use `Cipher.getInstance(String transformation)` with a transformation string that includes the mode and padding, e.g., `\"AES/CBC/PKCS5Padding\"`"
            )
        } else if (parts.size >= 2) {
            val mode = parts[1].trim().uppercase()
            if (mode == "ECB") {
                context.report(
                    GET_INSTANCE_ISSUE,
                    node,
                    context.getLocation(firstArg),
                    "Cipher.getInstance should not be called with ECB as the cipher mode: `ECB` mode is insecure"
                )
            }
        }
    }
}