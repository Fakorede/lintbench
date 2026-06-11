package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getReceiver

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "UsingBCProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                
                Reference documentation:
                  - https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                  - https://goo.gle/DeprecatedProvider
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        @VisibleForTesting
        val BCPROVIDER_METHOD_NAMES = listOf("getInstance")
    }

    override fun getApplicableMethodNames(): List<String>? {
        return BCPROVIDER_METHOD_NAMES
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (node.methodName == "getInstance") {
            val receiverType = context.evaluator.getType(node.getReceiver())
            if (receiverType?.canonicalText == "org.bouncycastle.jce.provider.BouncyCastleProvider") {
                val targetSdkVersion = context.client.getModule(context.file).getTargetSdkVersion()
                if (targetSdkVersion != null && targetSdkVersion >= SdkConstants.Android_P) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using BC Provider is deprecated for `targetSdkVersion` P or higher."
                    )
                }
            }
        }
    }

    @VisibleForTesting
    fun getTargetSdkVersion(context: JavaContext): Int? {
        return context.client.getModule(context.file).getTargetSdkVersion()
    }
}