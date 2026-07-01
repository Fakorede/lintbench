package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val GET_INSTANCE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            androidSpecific = true,
            moreInfo = "https://goo.gle/GetInstance",
            implementation = IMPLEMENTATION
        )

        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val GET_INSTANCE_METHOD = "getInstance"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, CIPHER_CLASS, false) &&
            containingClass.qualifiedName != CIPHER_CLASS
        ) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments - report issue (default ECB mode)
            context.report(
                GET_INSTANCE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode, " +
                        "as the default mode on Android is ECB, which is insecure."
            )
            return
        }

        // Evaluate the first argument (transformation string)
        val firstArg = arguments[0]
        val transformation = ConstantEvaluator.evaluateString(context, firstArg, false)
            ?: return // Can't determine statically, skip

        // Check if the transformation uses ECB mode or has no mode specified
        val parts = transformation.split("/")
        if (parts.size == 1) {
            // No mode specified (e.g., "AES") - default is ECB
            context.report(
                GET_INSTANCE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode " +
                        "because the default mode on Android is ECB, which is insecure."
            )
        } else if (parts.size >= 2) {
            val mode = parts[1].trim().uppercase()
            if (mode == "ECB") {
                context.report(
                    GET_INSTANCE,
                    node,
                    context.getLocation(node),
                    "Cipher.getInstance should not be called with ECB as the cipher mode."
                )
            }
        }
    }
}