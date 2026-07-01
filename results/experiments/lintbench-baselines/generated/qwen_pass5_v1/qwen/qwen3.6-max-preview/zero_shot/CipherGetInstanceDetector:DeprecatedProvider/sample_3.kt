package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import java.util.EnumSet

class CipherGetInstanceDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val args = node.valueArguments
        if (args.size < 2) return

        val providerArg = args[1]
        if (providerArg is ULiteralExpression && providerArg.value == "BC") {
            context.report(
                ISSUE,
                providerArg,
                context.getLocation(providerArg),
                "Using the `BC` provider is deprecated and will be removed on Android P and higher"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.\n\n" +
                    "Reference documentation:\n" +
                    "- https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html\n" +
                    "- https://goo.gle/DeprecatedProvider",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}