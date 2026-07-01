package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getParentOfType

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val transformation = context.evaluator.getConstantString(argument) ?: return

        if (transformation.contains("/ECB/", ignoreCase = true) ||
            transformation.equals("ECB", ignoreCase = true)
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called with ECB mode, which is insecure"
            )
        } else if (!transformation.contains("/")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Cipher.getInstance called without an explicit cipher mode; the default mode on Android is ECB, which is insecure"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Insecure use of Cipher.getInstance",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or without setting the cipher mode, because the default mode on Android is ECB, which is insecure.
            """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}