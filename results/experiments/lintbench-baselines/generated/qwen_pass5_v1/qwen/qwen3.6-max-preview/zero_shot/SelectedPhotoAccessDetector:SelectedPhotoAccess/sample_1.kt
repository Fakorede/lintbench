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

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.targetSdk < 34) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == PERMISSION_READ_MEDIA_IMAGES || name == PERMISSION_READ_MEDIA_VIDEO) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "On Android 14+ (API 34+), requesting `$name` triggers the Selected Photo Access flow, " +
                "which may grant only partial access to the photo library. " +
                "Consider using the Photo Picker (`MediaStore.ACTION_PICK_IMAGES`) or explicitly handling partial access. " +
                "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    companion object {
        private const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"

        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                your app to handle partial access to the photo library. Consider using the Photo Picker \
                (`MediaStore.ACTION_PICK_IMAGES`) which provides a privacy-friendly way to select photos \
                and videos without requiring broad storage permissions.

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
    }
}