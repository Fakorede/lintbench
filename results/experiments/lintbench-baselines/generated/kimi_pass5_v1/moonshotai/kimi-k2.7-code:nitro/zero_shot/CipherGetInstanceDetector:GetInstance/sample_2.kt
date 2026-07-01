package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isMemberInClass
import com.android.tools.lint.detector.api.isString
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getArgumentForParameter

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf("getInstance")

    override fun visitCall(
        context: JavaContext,
        call: UCallExpression,
        referenced: PsiMethod?
    ) {
        if (referenced == null || !context.evaluator.isMemberInClass(referenced, "javax.crypto.Cipher")) {
            return
        }

        val arg = call.getArgumentForParameter(0) ?: return
        val transformation = arg.evaluateString() ?: return

        if (transformation.contains("/")) {
            if (transformation.contains("/ECB/") || transformation.endsWith("/ECB")) {
                report(context, call, transformation)
            }
        } else {
            // No mode specified; the default mode on Android is ECB
            report(context, call, transformation)
        }
    }

    private fun report(context: JavaContext, call: UCallExpression, transformation: String) {
        val message = "Cipher.getInstance should not be called with ECB or without " +
                "explicitly setting a secure cipher mode (the default mode on Android is ECB, " +
                "which is insecure)."
        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            message
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Insecure use of Cipher.getInstance",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or
                without setting the cipher mode because the default mode on Android is ECB,
                which is insecure. Use a secure mode such as CBC or GCM instead
                (for example, `Cipher.getInstance("AES/GCM/NoPadding")`).
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
}