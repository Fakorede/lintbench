package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val USE_BC_PROVIDER = Issue.create(
            "UseOfBCProvider",
            "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
            "Using the `BC` (Bouncy Castle) provider in your application can lead to runtime failures if your app targets Android P or later. Consider using alternative providers that are supported on all versions of Android.",
            Category.SECURITY,
            6, // Priority
            Severity.ERROR,
            Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val BC_PROVIDER = "BC"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        if (node.receiver?.let { context.evaluator.getType(it)?.canonicalText } == "javax.crypto.Cipher") {
            val arguments = node.valueArguments
            if (arguments.size >= 2) {
                val providerArgument = arguments[1]
                val providerValue = when (providerArgument) {
                    is ULiteralExpression -> providerArgument.asRenderString()
                    else -> null
                }

                if (BC_PROVIDER == providerValue) {
                    context.report(
                        USE_BC_PROVIDER,
                        node,
                        context.getLocation(node),
                        "The BC provider is deprecated and should not be used."
                    )
                }
            }
        }
    }

    override fun visitElement(context: JavaContext, node: UElement): Boolean {
        if (node is UCallExpression) {
            val methodName = node.methodName
            if ("getTargetSdkVersion" == methodName) {
                val targetSdkVersionValue = context.evaluator.evaluate(node)?.toIntOrNull() ?: return true

                if (isAtLeastP(targetSdkVersionValue)) {
                    context.report(
                        USE_BC_PROVIDER,
                        node,
                        context.getLocation(node),
                        "The BC provider is deprecated for `targetSdkVersion` P or higher."
                    )
                }
            }
        }
        return super.visitElement(context, node)
    }

    private fun isAtLeastP(sdkVersion: Int): Boolean {
        return sdkVersion >= 28
    }
}