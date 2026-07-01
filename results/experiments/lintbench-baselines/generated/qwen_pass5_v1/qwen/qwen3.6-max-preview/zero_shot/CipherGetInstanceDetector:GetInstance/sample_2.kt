package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UastScanner
import java.util.EnumSet

class CipherGetInstanceDetector : Detector(), UastScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") return

        val args = node.valueArguments
        if (args.isEmpty()) return

        val transformationArg = args[0]
        val transformation = context.evaluator.getStringValue(transformationArg) ?: return

        if (isInsecureTransformation(transformation)) {
            context.report(
                ISSUE,
                context.getLocation(transformationArg),
                "Cipher.getInstance should not be called with ECB mode or without specifying a mode, as ECB is insecure."
            )
        }
    }

    private fun isInsecureTransformation(transformation: String): Boolean {
        val parts = transformation.split("/")
        val mode = parts.getOrNull(1)?.trim()?.uppercase()
        return mode.isNullOrEmpty() || mode == "ECB"
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }
}