package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

private const val ANDROID_P = 28

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The Bouncy Castle ("BC") provider has been deprecated on Android. When
                targetSdkVersion is Android P (API 28) or higher, the BC provider is not
                available, so calls such as Cipher.getInstance(..., "BC") will fail at
                runtime.

                Use Cipher.getInstance(String) to use the default provider, or switch to a
                provider that is supported on Android P and later.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/DeprecatedProvider",
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != "javax.crypto.Cipher") {
            return
        }

        val provider = node.valueArguments.getOrNull(1) ?: return
        if (provider.getStringValue() == "BC") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "The BC provider is deprecated and is not available when targetSdkVersion is P or higher.",
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.mainProject.targetSdkVersion >= ANDROID_P
    }

    private fun UExpression.getStringValue(): String? {
        return (this as? ULiteralExpression)?.value as? String
    }
}