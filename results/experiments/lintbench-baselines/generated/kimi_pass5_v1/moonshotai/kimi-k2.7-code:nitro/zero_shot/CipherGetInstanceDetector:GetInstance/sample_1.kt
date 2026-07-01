package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val argument = call.valueArguments.firstOrNull() ?: return
        val transformation = ConstantEvaluator.evaluate(context, argument) as? String ?: return

        val mode = transformation.trim().split("/").getOrNull(1) ?: ""
        if (mode.isEmpty() || mode.equals("ECB", ignoreCase = true)) {
            val message = "Cipher.getInstance should not use ECB mode or omit the mode; " +
                    "the default mode on Android is ECB, which is insecure."
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                message
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or
                without setting the cipher mode because the default mode on Android is ECB,
                which is insecure.

                Reference: https://goo.gle/GetInstance
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}