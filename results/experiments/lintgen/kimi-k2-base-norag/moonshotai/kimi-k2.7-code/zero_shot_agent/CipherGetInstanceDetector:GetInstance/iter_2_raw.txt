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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or
                without setting the cipher mode because the default mode on Android is ECB,
                which is insecure.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/GetInstance"
        )
    }

    override fun getApplicableCallNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val transformation = call.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluate(context, transformation) as? String ?: return

        if (isInsecure(value)) {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "This `Cipher.getInstance` call uses ECB or does not specify a cipher mode, which is insecure."
            )
        }
    }

    private fun isInsecure(transformation: String): Boolean {
        val trimmed = transformation.trim()
        val firstSlash = trimmed.indexOf('/')
        if (firstSlash == -1) {
            return true
        }

        val secondSlash = trimmed.indexOf('/', firstSlash + 1)
        val mode = if (secondSlash == -1) {
            trimmed.substring(firstSlash + 1)
        } else {
            trimmed.substring(firstSlash + 1, secondSlash)
        }

        return mode.isEmpty() || mode.equals("ECB", ignoreCase = true)
    }
}