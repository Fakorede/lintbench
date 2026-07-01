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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.resolveToUElement

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf("getInstance")

    override fun visitCall(context: JavaContext, call: UCallExpression) {
        if (!context.evaluator.isMemberInClass(
                call.resolve(),
                "javax.crypto.Cipher"
            )
        ) {
            return
        }

        val transformationArg = call.valueArguments.firstOrNull() ?: return
        val transformation =
            ConstantEvaluator.evaluate(context, transformationArg) as? String ?: return

        val parts = transformation.split("/")
        val isInsecure = when {
            parts.size == 1 -> true
            parts.size >= 2 && parts[1].equals("ECB", ignoreCase = true) -> true
            else -> false
        }

        if (isInsecure) {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Cipher.getInstance should not be called with ECB or without a cipher mode. "
                        + "The default mode on Android is ECB, which is insecure. "
                        + "See https://goo.gle/GetInstance"
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode, because the default mode on Android is ECB, \
                which is insecure. Use a secure mode such as CBC or GCM.
                
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