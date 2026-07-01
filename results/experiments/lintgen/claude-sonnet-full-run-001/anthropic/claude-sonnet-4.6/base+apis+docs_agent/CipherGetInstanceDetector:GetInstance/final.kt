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

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Check that this is Cipher.getInstance
        if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            // No arguments - report issue
            context.report(
                GET_INSTANCE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode and padding"
            )
            return
        }

        // Evaluate the first argument (the transformation string)
        val firstArg = arguments[0]
        val transformationValue = ConstantEvaluator.evaluate(context, firstArg)

        if (transformationValue == null) {
            // Can't determine the value statically - check if it might be a string literal
            // If we can't evaluate it, we skip (conservative approach)
            return
        }

        val transformation = transformationValue.toString()

        // Check if the transformation contains ECB or has no mode specified
        // A full transformation is "Algorithm/Mode/Padding"
        // If there's no "/" then no mode is specified (defaults to ECB on Android)
        val parts = transformation.split("/")

        if (parts.size == 1) {
            // No mode specified - defaults to ECB
            context.report(
                GET_INSTANCE,
                node,
                context.getLocation(node),
                "Cipher.getInstance should not be called without setting the cipher mode and padding: `$transformation`"
            )
        } else if (parts.size >= 2) {
            val mode = parts[1].trim().uppercase()
            if (mode == "ECB") {
                context.report(
                    GET_INSTANCE,
                    node,
                    context.getLocation(node),
                    "Cipher.getInstance should not be called with ECB as the cipher mode: `$transformation`"
                )
            }
        }
    }
}