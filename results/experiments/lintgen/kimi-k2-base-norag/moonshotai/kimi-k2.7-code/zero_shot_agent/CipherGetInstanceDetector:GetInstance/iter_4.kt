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
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) {
            return
        }

        val value = ConstantEvaluator.evaluate(context, args[0]) ?: return
        val transformation = value as? String ?: return

        val trimmed = transformation.trim()
        if (!trimmed.contains("/") || trimmed.contains("/ECB", ignoreCase = true)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called with ECB mode or without a mode; the default mode on Android is ECB, which is insecure."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                Calling `Cipher.getInstance` with ECB mode or without specifying a mode is insecure.
                The default mode on Android is ECB, which is not secure. Use a secure mode such as
                `AES/GCM/NoPadding` or `AES/CBC/PKCS5Padding` instead.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/GetInstance"
        )
    }
}