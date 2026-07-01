package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (context.mainProject.targetSdkVersion.apiLevel < 28) {
                    return
                }

                val method = node.resolve() ?: return
                if (method.name != "getInstance") return
                if (!context.evaluator.isMemberInClass(method, CIPHER_CLASS)) return
                if (node.valueArguments.size < 2) return

                val providerArg = node.getArgumentForParameter(1) ?: return
                val providerName = ConstantEvaluator.evaluate(context, providerArg) as? String

                if (providerName == PROVIDER_NAME) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "The BC provider has been deprecated and will not be provided on Android P and later."
                    )
                }
            }
        }

    companion object {
        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val PROVIDER_NAME = "BC"

        val ISSUE: Issue = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.

                Use a different provider (for example the default AndroidOpenSSL/Conscrypt provider) or include the Bouncy Castle library as an application dependency.

                Reference:
                https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                https://goo.gle/DeprecatedProvider
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