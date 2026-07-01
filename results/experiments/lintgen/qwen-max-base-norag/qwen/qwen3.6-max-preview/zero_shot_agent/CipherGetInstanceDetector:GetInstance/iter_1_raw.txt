package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {
    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val transformationArg = args[0]
        val transformation = transformationArg.evaluate() as? String ?: return

        if (isInsecureTransformation(transformation)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(transformationArg),
                "Cipher.getInstance should not be called with ECB mode or without specifying a mode."
            )
        }
    }

    private fun isInsecureTransformation(transformation: String): Boolean {
        if (transformation.isBlank()) return false
        val parts = transformation.split("/")
        if (parts.size == 1) return true
        val mode = parts.getOrNull(1)?.trim()
        return mode.equals("ECB", ignoreCase = true)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is ECB, \
                which is insecure.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}