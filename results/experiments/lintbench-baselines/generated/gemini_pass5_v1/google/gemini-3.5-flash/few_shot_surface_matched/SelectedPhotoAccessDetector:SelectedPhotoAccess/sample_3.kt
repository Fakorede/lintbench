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
        val project = context.mainProject
        val document = project.mergedManifest ?: return

        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasReadMediaVisualUserSelected = false

        val permissionNodes = document.getElementsByTagName("uses-permission")
        for (i in 0 until permissionNodes.length) {
            val item = permissionNodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.permission.READ_MEDIA_IMAGES") {
                hasReadMediaImages = true
            } else if (name == "android.permission.READ_MEDIA_VIDEO") {
                hasReadMediaVideo = true
            } else if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                hasReadMediaVisualUserSelected = true
            }
        }

        if ((hasReadMediaImages || hasReadMediaVideo) && !hasReadMediaVisualUserSelected) {
            val manifestFile = project.manifestFiles.firstOrNull()
            val location = if (manifestFile != null) {
                Location.create(manifestFile)
            } else {
                Location.create(project.dir)
            }
            context.report(
                ISSUE,
                location,
                "When requesting photo library access, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support the Selected Photo Access feature on Android 14+"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "Selected Photo Access is a new ability for users to share partial access to their photo library when apps request access to their device storage on Android 14+.\n\nInstead of letting the system manage the selection lifecycle, we recommend you adapt your app to handle partial access to the photo library.",
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