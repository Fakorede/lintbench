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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val documentElement = mergedManifest.documentElement ?: return

        var usesPermission = getFirstSubTagByName(documentElement, TAG_USES_PERMISSION)
        while (usesPermission != null) {
            val name = usesPermission.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                // Already handling selected photo access
                return
            }
            usesPermission = getNextTagByName(usesPermission, TAG_USES_PERMISSION)
        }

        var hasReadMediaImages = false
        var hasReadMediaVideo = false

        usesPermission = getFirstSubTagByName(documentElement, TAG_USES_PERMISSION)
        while (usesPermission != null) {
            val name = usesPermission.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.permission.READ_MEDIA_IMAGES") {
                hasReadMediaImages = true
            } else if (name == "android.permission.READ_MEDIA_VIDEO") {
                hasReadMediaVideo = true
            }
            usesPermission = getNextTagByName(usesPermission, TAG_USES_PERMISSION)
        }

        if (!hasReadMediaImages && !hasReadMediaVideo) {
            return
        }

        // Find the element to report on (first READ_MEDIA_IMAGES or READ_MEDIA_VIDEO)
        usesPermission = getFirstSubTagByName(documentElement, TAG_USES_PERMISSION)
        while (usesPermission != null) {
            val name = usesPermission.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.permission.READ_MEDIA_IMAGES" ||
                name == "android.permission.READ_MEDIA_VIDEO"
            ) {
                val xmlContext = context as? XmlContext
                if (xmlContext != null) {
                    val attribute = usesPermission.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                    val location = if (attribute != null) {
                        xmlContext.getValueLocation(attribute)
                    } else {
                        xmlContext.getLocation(usesPermission)
                    }
                    xmlContext.report(ISSUE, usesPermission, location, MESSAGE)
                } else {
                    context.report(
                        issue = ISSUE,
                        location = context.project.dir.let {
                            com.android.tools.lint.detector.api.Location.create(it)
                        },
                        message = MESSAGE
                    )
                }
                return
            }
            usesPermission = getNextTagByName(usesPermission, TAG_USES_PERMISSION)
        }
    }

    companion object {
        private const val MESSAGE =
            "Selected Photo Access is a new ability for users to share partial access to " +
                "their photo library when apps request access to their device storage on " +
                "Android 14+. Instead of letting the system manage the selection lifecycle, " +
                "we recommend you adapt your app to handle partial access to the photo library " +
                "by adding the `READ_MEDIA_VISUAL_USER_SELECTED` permission. See " +
                "https://developer.android.com/about/versions/14/changes/partial-photo-video-access " +
                "for more details."

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation =
                "Selected Photo Access is a new ability for users to share partial access to " +
                    "their photo library when apps request access to their device storage on " +
                    "Android 14+.\n\n" +
                    "Instead of letting the system manage the selection lifecycle, we recommend " +
                    "you adapt your app to handle partial access to the photo library by " +
                    "declaring the `READ_MEDIA_VISUAL_USER_SELECTED` permission.\n\n" +
                    "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access " +
                    "for more details.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo = "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }
}