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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANDROID_P = 28

        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` (Bouncy Castle) provider has been deprecated and is no longer
                available to apps that target Android P (API 28) or higher.
                Calls such as `Cipher.getInstance(..., "BC")` will throw
                `NoSuchProviderException` on Android P and above. You should remove the
                explicit provider argument and use the default provider instead.
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
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        if (node.valueArgumentCount < 2) {
            return
        }

        val providerName = node.valueArguments[1].evaluate() as? String ?: return
        if (providerName != "BC") {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Bouncy Castle provider \"BC\" is deprecated and not available when targeting Android P or higher",
        )
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.project.targetSdk >= ANDROID_P
    }
}