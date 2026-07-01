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

    override fun getApplicableCallNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val arg = call.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluate(context, arg) as? String ?: return

        val parts = value.split("/")
        if (parts.size <= 1 || parts[1].equals("ECB", ignoreCase = true)) {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Calling Cipher.getInstance without specifying a cipher mode or with " +
                    "ECB mode is insecure; the default mode on Android is ECB."
            )
        }
    }

    companion object {
        @JvmStatic
        val ISSUE = Issue.create(
            "GetInstance",
            "Cipher.getInstance with ECB",
            """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or
                without setting the cipher mode because the default mode on Android is
                ECB, which is insecure.
            """.trimIndent(),
            Category.SECURITY,
            9,
            Severity.WARNING,
            Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
            "https://goo.gle/GetInstance"
        )
    }
}