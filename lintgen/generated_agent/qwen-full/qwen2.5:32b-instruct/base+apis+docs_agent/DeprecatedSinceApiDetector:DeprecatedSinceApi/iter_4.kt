package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.DEFAULT_MIN_SDK_VERSION
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ISSUE_ID = "DeprecatedSinceApi"
        private const val BRIEF_DESCRIPTION = "Using a method deprecated in earlier SDK"
        private const val EXPLANATION =
            """
            Some backport methods are only necessary until a specific version of Android. These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions.
            Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """

        val ISSUE = Issue.create(
            id = ISSUE_ID,
            briefDescription = BRIEF_DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return null
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val deprecatedAnnotation = method.annotationOfType() ?: return

        val minSdkVersion = context.getModuleMinSdkVersion()
        if (minSdkVersion >= deprecatedAnnotation.deprecatedApiLevel) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Call to ${method.name} is unnecessary since minSdkVersion (${minSdkVersion}) is already at or above the deprecated API level (${deprecatedAnnotation.deprecatedApiLevel})"
            )
        }
    }

    @VisibleForTesting
    fun JavaContext.getModuleMinSdkVersion(): Int {
        val manifestFile = project.files.find { it.path.endsWith(ANDROID_MANIFEST_XML) } ?: return DEFAULT_MIN_SDK_VERSION
        val minSdkNode = manifestFile.xml?.getAttributeValue("manifest", ANDROID_MANIFEST_XML, ATTR_MIN_SDK_VERSION)
        return if (minSdkNode != null) {
            try {
                Integer.parseInt(minSdkNode)
            } catch (e: NumberFormatException) {
                DEFAULT_MIN_SDK_VERSION
            }
        } else {
            DEFAULT_MIN_SDK_VERSION
        }
    }

    private fun PsiMethod.annotationOfType(): DeprecatedSinceApi? {
        val annotation = this.annotations.find { it.qualifiedName == "DeprecatedSinceApi" }
        return if (annotation != null) {
            val deprecatedApiLevelAttr = annotation.findAttributeValue("value")
            if (deprecatedApiLevelAttr is UExpression && deprecatedApiLevelAttr.asSourceString().isNotEmpty()) {
                try {
                    DeprecatedSinceApi(deprecatedApiLevelAttr.asInt())
                } catch (e: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    private data class DeprecatedSinceApi(val deprecatedApiLevel: Int)
}