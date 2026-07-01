package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluate

class CipherGetInstanceDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        if (!isCryptoClass(qualifiedName)) return

        for (arg in node.valueArguments) {
            if (arg.evaluate() == "BC") {
                context.report(
                    ISSUE,
                    arg,
                    context.getLocation(arg),
                    "Using the `BC` provider is deprecated and will not be available when targeting Android P or higher"
                )
            }
        }
    }

    private fun isCryptoClass(qualifiedName: String): Boolean {
        return qualifiedName.startsWith("java.security.") ||
               qualifiedName.startsWith("javax.crypto.") ||
               qualifiedName.startsWith("android.security.")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.

                Reference documentation:
                - https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                - https://goo.gle/DeprecatedProvider
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