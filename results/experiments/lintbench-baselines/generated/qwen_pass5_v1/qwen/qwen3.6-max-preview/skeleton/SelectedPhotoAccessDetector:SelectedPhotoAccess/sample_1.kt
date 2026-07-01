package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.Detector.XmlScanner
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "Android 14 introduces Selected Photo Access, allowing users to grant partial access " +
                "to their photo library. When requesting READ_MEDIA_IMAGES or READ_MEDIA_VIDEO, users may choose " +
                "to share only specific photos or videos. Instead of letting the system manage the selection lifecycle, " +
                "you should adapt your app to handle partial access by checking for the READ_MEDIA_VISUAL_USER_SELECTED " +
                "permission or gracefully handling limited library access.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        // Manifest scanning is handled via the XmlScanner interface callbacks
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttribute("android:name")
        if (permissionName == "android.permission.READ_MEDIA_IMAGES" ||
            permissionName == "android.permission.READ_MEDIA_VIDEO") {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Requesting $permissionName may result in partial photo/video access on Android 14+. " +
                "Ensure your app handles READ_MEDIA_VISUAL_USER_SELECTED or adapts to limited library access."
            )
        }
    }
}