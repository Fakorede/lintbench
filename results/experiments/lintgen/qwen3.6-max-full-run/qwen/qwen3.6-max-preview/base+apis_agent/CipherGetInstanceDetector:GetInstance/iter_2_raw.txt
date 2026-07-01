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
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = "`Cipher#getInstance` should not be called with ECB as the cipher mode or " +
                    "without setting the cipher mode because the default mode on android is ECB, which is insecure.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val transformation = context.evaluator.evaluate(firstArg) as? String ?: return

        val parts = transformation.split('/')
        val mode = parts.getOrNull(1)
        val isEcbOrDefault = mode == null || mode.equals("ECB", ignoreCase = true)

        if (isEcbOrDefault) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called with ECB mode or without specifying a mode (defaults to ECB)"
            )
        }
    }
}