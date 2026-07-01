package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    private var hasReadMediaImages = false
    private var hasReadMediaVideo = false
    private var hasReadMediaVisualUserSelected = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        when (name) {
            "android.permission.READ_MEDIA_IMAGES" -> hasReadMediaImages = true
            "android.permission.READ_MEDIA_VIDEO" -> hasReadMediaVideo = true
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasReadMediaVisualUserSelected = true
        }
    }

    override fun checkMergedProject(context: Context) {
        val requestsMediaPermissions = hasReadMediaImages || hasReadMediaVideo
        if (requestsMediaPermissions && !hasReadMediaVisualUserSelected) {
            context.report(
                ISSUE,
                context.project.manifestFiles.firstOrNull()?.let {
                    context.getLocation(it)
                } ?: return,
                "To handle the `Selected Photos Access` introduced in Android 14, you should add " +
                    "`READ_MEDIA_VISUAL_USER_SELECTED` to your manifest. If you do not, your app " +
                    "will be considered as having requested full photo/video access on Android 14+ " +
                    "devices. See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation =
                """
                Selected Photo Access is a new ability for users to share partial access to their \
                photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you \
                adapt your app to handle partial access to the photo library.

                To do this, add `READ_MEDIA_VISUAL_USER_SELECTED` to your manifest alongside \
                `READ_MEDIA_IMAGES` and/or `READ_MEDIA_VIDEO`.

                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access \
                for more details.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}