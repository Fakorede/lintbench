package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class CipherGetInstanceDetector : Detector(), SourceCodeScanner, XmlScanner {
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
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        private val BC_PROVIDER = "BC"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-sdk")
    }

    override fun visitElement(@NonNull context: XmlContext, @NonNull element: Element) {
        if (element.tagName == "uses-sdk") {
            val minSdkVersionAttr = element.getAttribute("android:minSdkVersion")
            val targetSdkVersionAttr = element.getAttribute("android:targetSdkVersion")

            val sdkIntComparator = SdkIntComparator()
            val targetSdkVersion = targetSdkVersionAttr.toIntOrNull() ?: return
            if (sdkIntComparator.isAtLeastP(targetSdkVersion)) {
                context.report(
                    USE_BC_PROVIDER,
                    context.getLocation(element),
                    "The BC provider is deprecated for `targetSdkVersion` P or higher."
                )
            }
        }
    }

    override fun visitJavaElement(context: JavaContext, element: UElement): Boolean {
        if (element is UCallExpression) {
            val methodName = element.methodName
            val receiverType = element.receiver?.let { context.resolve(it)?.type } ?: return false

            if ("getInstance" == methodName && "javax.crypto.Cipher" == receiverType.canonicalText) {
                val arguments = element.valueArguments
                if (arguments.size >= 2) {
                    val providerArgument = arguments[1]
                    val providerValue = when (providerArgument) {
                        is ULiteralExpression -> providerArgument.value as String?
                        else -> null
                    }

                    if (BC_PROVIDER == providerValue) {
                        context.report(
                            USE_BC_PROVIDER,
                            element,
                            context.getLocation(element),
                            "The BC provider is deprecated and should not be used."
                        )
                    }
                }
            }
        }
        return true
    }
}