package com.android.tools.lint.checks

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
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME)
        if (name == "android.permission.READ_MEDIA_IMAGES" || name == "android.permission.READ_MEDIA_VIDEO") {
            context.report(
                ISSUE,
                context.getNameLocation(element),
                "On Android 14+, users can grant partial access to their photo library. " +
                        "Ensure your app handles selected photo access gracefully. " +
                        "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "SelectedPhotoAccess",
            "Behavior change when requesting photo library access",
            "Selected Photo Access is a new ability for users to share partial access to their photo library " +
                    "when apps request access to their device storage on Android 14+. Instead of letting the system " +
                    "manage the selection lifecycle, we recommend you adapt your app to handle partial access to the photo library.",
            Category.PERMISSIONS,
            6,
            Severity.WARNING,
            Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}