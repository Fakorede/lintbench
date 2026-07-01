package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import java.util.EnumSet

class CipherGetInstanceDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        if (!qualifiedName.startsWith("java.security.") && !qualifiedName.startsWith("javax.crypto.")) {
            return
        }

        for (arg in node.valueArguments) {
            if (arg is ULiteralExpression && arg.value == "BC") {
                context.report(
                    ISSUE,
                    context.getLocation(arg),
                    "Using the `BC` provider is deprecated and will not be provided when `targetSdkVersion` is P or higher."
                )
                break
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}