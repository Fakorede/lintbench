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
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: XmlContext) {
        val document = context.document ?: return
        val root = document.documentElement ?: return

        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasReadMediaVisualUserSelected = false

        var tag = getFirstSubTagByName(root, TAG_USES_PERMISSION)
        while (tag != null) {
            val name = tag.getAttributeNS(ANDROID_URI, ATTR_NAME)
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> hasReadMediaImages = true
                "android.permission.READ_MEDIA_VIDEO" -> hasReadMediaVideo = true
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasReadMediaVisualUserSelected = true
            }
            tag = getNextTagByName(tag, TAG_USES_PERMISSION)
        }

        if ((hasReadMediaImages || hasReadMediaVideo) && !hasReadMediaVisualUserSelected) {
            // Find the element to report on
            var reportElement: Element? = null
            var searchTag = getFirstSubTagByName(root, TAG_USES_PERMISSION)
            while (searchTag != null) {
                val name = searchTag.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "android.permission.READ_MEDIA_IMAGES" ||
                    name == "android.permission.READ_MEDIA_VIDEO"
                ) {
                    reportElement = searchTag
                    break
                }
                searchTag = getNextTagByName(searchTag, TAG_USES_PERMISSION)
            }

            if (reportElement != null) {
                val attribute = reportElement.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                val location = if (attribute != null) {
                    context.getValueLocation(attribute)
                } else {
                    context.getLocation(reportElement)
                }
                context.report(
                    ISSUE,
                    reportElement,
                    location,
                    "Selected Photo Access is a new ability for users to share partial " +
                        "access to their photo library. Instead of letting the system manage " +
                        "the selection lifecycle, consider adding " +
                        "`READ_MEDIA_VISUAL_USER_SELECTED` to handle partial access to the " +
                        "photo library on Android 14+. See " +
                        "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation =
                """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on \
                Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library by also \
                requesting the `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside \
                `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.

                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access
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