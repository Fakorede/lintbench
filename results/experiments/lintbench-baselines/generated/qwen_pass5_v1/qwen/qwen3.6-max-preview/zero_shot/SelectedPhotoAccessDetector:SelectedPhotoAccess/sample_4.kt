package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.android.tools.lint.detector.api.UastScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner, UastScanner {

    companion object {
        private const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private val TARGET_PERMISSIONS = setOf(PERMISSION_READ_MEDIA_IMAGES, PERMISSION_READ_MEDIA_VIDEO)

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt your app \
                to handle partial access to the photo library.

                Reference documentation:
                https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableXmlNames(): Collection<String> = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permissionName in TARGET_PERMISSIONS) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Requesting `$permissionName` triggers partial photo/video access on Android 14+. " +
                    "Ensure your app handles partial access gracefully or consider using the Photo Picker API."
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.evaluate() as? String ?: return
                if (value in TARGET_PERMISSIONS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Requesting `$value` triggers partial photo/video access on Android 14+. " +
                            "Ensure your app handles partial access gracefully or consider using the Photo Picker API."
                    )
                }
            }
        }
    }
}