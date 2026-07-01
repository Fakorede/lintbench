package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Android 14 (API 34) introduces Selected Photo Access, which allows users to grant \
                partial access to their photo library when apps request READ_MEDIA_IMAGES or READ_MEDIA_VIDEO. \
                Instead of letting the system manage the selection lifecycle, you should adapt your app to \
                handle partial access by checking for the READ_MEDIA_VISUAL_USER_SELECTED permission state, \
                or migrate to the system Photo Picker for a more privacy-friendly approach.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permission == READ_MEDIA_IMAGES || permission == READ_MEDIA_VIDEO) {
            val location = context.getLocation(element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME))
            context.report(
                ISSUE,
                location,
                "Requesting `$permission` triggers partial photo/video access on Android 14+. " +
                    "Ensure your app handles `READ_MEDIA_VISUAL_USER_SELECTED` or considers using the Photo Picker."
            )
        }
    }
}