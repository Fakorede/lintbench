package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val transformation = firstArg.evaluateString() ?: return

        val parts = transformation.split("/")
        val algorithm = parts[0].uppercase()

        if (algorithm in SYMMETRIC_BLOCK_CIPHERS) {
            if (parts.size == 1) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(firstArg),
                    "`Cipher.getInstance` should not be called without setting the cipher mode"
                )
            } else if (parts.size >= 2) {
                val mode = parts[1].uppercase()
                if (mode == "ECB") {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(firstArg),
                        "ECB mode should not be used"
                    )
                }
            }
        }
    }

    companion object {
        private val SYMMETRIC_BLOCK_CIPHERS = setOf(
            "AES", "DES", "DESEDE", "BLOWFISH", "RC2"
        )

        @JvmField
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
            moreInfo = "https://goo.gle/GetInstance",
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}