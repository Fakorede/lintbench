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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """.trimIndent(),
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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluate() as? String ?: return

        val parts = value.split("/")
        if (parts.isEmpty()) return

        val algorithm = parts[0].uppercase()
        if (algorithm == "RSA" || algorithm == "ECIES") {
            return
        }

        if (parts.size == 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "`Cipher.getInstance` should not be called without setting the cipher mode"
            )
        } else if (parts.size >= 2 && parts[1].uppercase() == "ECB") {
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "ECB mode should not be used"
            )
        }
    }
}