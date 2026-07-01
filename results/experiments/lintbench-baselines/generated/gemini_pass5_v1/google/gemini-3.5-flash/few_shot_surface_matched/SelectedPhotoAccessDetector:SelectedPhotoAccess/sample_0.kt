package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        val usesPermissions = mergedManifest.getElementsByTagName("uses-permission")
        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasVisualUserSelected = false

        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.permission.READ_MEDIA_IMAGES") {
                hasReadMediaImages = true
            } else if (name == "android.permission.READ_MEDIA_VIDEO") {
                hasReadMediaVideo = true
            } else if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                hasVisualUserSelected = true
            }
        }

        if ((hasReadMediaImages || hasReadMediaVideo) && !hasVisualUserSelected) {
            val manifestFile = context.mainProject.manifestFiles.firstOrNull()
            val reportLocation = if (manifestFile != null) {
                Location.create(manifestFile)
            } else {
                Location.create(context.project.dir)
            }
            context.report(
                ISSUE,
                reportLocation,
                "The app requests photo or video access but does not declare `READ_MEDIA_VISUAL_USER_SELECTED`. " +
                "To support the Selected Photos Access feature on Android 14+, declare this permission."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "Selected Photo Access is a new ability for users to share partial access to their photo library " +
                    "when apps request access to their device storage on Android 14+.\n\n" +
                    "Instead of letting the system manage the selection lifecycle, we recommend you adapt " +
                    "your app to handle partial access to the photo library.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}