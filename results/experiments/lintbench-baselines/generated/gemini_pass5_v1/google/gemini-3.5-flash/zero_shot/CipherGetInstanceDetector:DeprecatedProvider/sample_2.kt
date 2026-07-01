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
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isJcaClass = containingClass.startsWith("java.security.") ||
                containingClass.startsWith("javax.crypto.") ||
                containingClass.startsWith("javax.net.ssl.")

        if (!isJcaClass) return

        val args = node.valueArguments
        if (args.size < 2) return

        val providerArg = args[1]
        if (isBcProvider(providerArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(providerArg),
                "The `BC` provider is deprecated and should not be used"
            )
        }
    }

    private fun isBcProvider(expression: UExpression): Boolean {
        val constant = expression.evaluate()
        if (constant is String && constant.equals("BC", ignoreCase = true)) {
            return true
        }

        if (expression is UCallExpression) {
            val method = expression.resolve()
            if (method != null && method.name == "getProvider") {
                val containingClass = method.containingClass?.qualifiedName
                if (containingClass == "java.security.Security") {
                    val args = expression.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0].evaluate()
                        if (firstArg is String && firstArg.equals("BC", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                
                Avoid specifying a provider and let the platform choose, or use a recommended provider.
                """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).addMoreInfo("https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html")
         .addMoreInfo("https://goo.gle/DeprecatedProvider")
    }
}