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
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CIPHER_CLASS = "javax.crypto.Cipher"
        private const val BC_PROVIDER = "BC"
        private const val ANDROID_P_API_LEVEL = 28

        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` (BouncyCastle) provider has been deprecated in Android and is \
                not provided when `targetSdkVersion` is Android P (API 28) or higher. \
                Calls to `Cipher.getInstance(..., "BC")` will fail at runtime on those \
                devices. Use the default provider or another supported provider instead.
            """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            moreInfo = "https://goo.gle/DeprecatedProvider",
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!isCipherGetInstance(method)) {
            return
        }

        val args = node.valueArguments
        if (args.size < 2) {
            return
        }

        val providerName = args[1].evaluateString()
        if (providerName == BC_PROVIDER) {
            val message =
                "Using the deprecated `BC` provider; it is not available when " +
                        "targetSdkVersion is P or higher."
            val incident = Incident(ISSUE, node, context.getLocation(node), message)
            context.report(incident)
        }
    }

    private fun isCipherGetInstance(method: PsiMethod): Boolean {
        return method.name == "getInstance" &&
                method.containingClass?.qualifiedName == CIPHER_CLASS
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.mainProject.targetSdk
        return targetSdk < 0 || targetSdk >= ANDROID_P_API_LEVEL
    }
}