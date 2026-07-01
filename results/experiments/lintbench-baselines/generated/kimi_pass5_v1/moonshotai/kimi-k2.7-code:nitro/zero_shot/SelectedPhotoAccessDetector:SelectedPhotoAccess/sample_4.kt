package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaElementVisitor
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val TARGET_SDK_THRESHOLD = 34
        private const val REQUEST_PERMISSIONS = "requestPermissions"
        private const val CHECK_SELF_PERMISSION = "checkSelfPermission"

        private val SENSITIVE_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )

        @JvmField
        val ISSUE = Issue.create(
            "SelectedPhotoAccess",
            "Behavior change when requesting photo library access",
            """
                On Android 14+ (API 34+), users can grant only partial access to photos and videos when an app requests storage permissions.

                To adapt, use the Android Photo Picker (ActivityResultContracts.PickVisualMedia / PickMultipleVisualMedia) or Storage Access Framework, which don't require broad storage permissions. If your app does require runtime permissions, declare and request READ_MEDIA_VISUAL_USER_SELECTED alongside READ_MEDIA_IMAGES and READ_MEDIA_VIDEO, and be prepared to handle partial grants.

                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access for more information.
            """.trimIndent(),
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf(REQUEST_PERMISSIONS, CHECK_SELF_PERMISSION)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: JavaElementVisitor
    ) {
        if (context.mainProject.targetSdk < TARGET_SDK_THRESHOLD) return

        if (node.valueArguments.any { containsSensitivePermission(context, it) }) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Selected photo access on Android 14+ affects this permission request. " +
                    "Consider using the photo picker or handling partial access explicitly."
            )
        }
    }

    private fun containsSensitivePermission(context: JavaContext, expr: UExpression): Boolean {
        val value = ConstantEvaluator.evaluate(context, expr)
        return when (value) {
            is String -> value in SENSITIVE_PERMISSIONS
            is Array<*> -> value.any { it is String && it in SENSITIVE_PERMISSIONS }
            is Collection<*> -> value.any { it is String && it in SENSITIVE_PERMISSIONS }
            else -> false
        }
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.mainProject.targetSdk < TARGET_SDK_THRESHOLD) return

        val name = if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)) {
            element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        } else {
            element.getAttribute(SdkConstants.ATTR_NAME)
        }

        if (name in SENSITIVE_PERMISSIONS) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Declaring $name is affected by selected photo access behavior on Android 14+. " +
                    "Consider using the photo picker or declaring READ_MEDIA_VISUAL_USER_SELECTED to support partial access."
            )
        }
    }
}