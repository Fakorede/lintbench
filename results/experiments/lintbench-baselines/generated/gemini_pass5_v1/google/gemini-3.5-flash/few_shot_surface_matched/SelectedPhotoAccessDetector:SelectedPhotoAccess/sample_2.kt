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
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        val targetSdk = project.targetSdkVersion.apiLevel
        if (targetSdk < 34) {
            return
        }

        val document = project.mergedManifest ?: return
        val root = document.documentElement ?: return
        val usesPermissions = root.getElementsByTagName("uses-permission")
        var hasImages = false
        var hasVideo = false
        var hasVisualUserSelected = false

        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.permission.READ_MEDIA_IMAGES") {
                hasImages = true
            } else if (name == "android.permission.READ_MEDIA_VIDEO") {
                hasVideo = true
            } else if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                hasVisualUserSelected = true
            }
        }

        if ((hasImages || hasVideo) && !hasVisualUserSelected) {
            val manifestFile = project.manifestFiles.firstOrNull()
            val location = if (manifestFile != null) {
                context.getLocation(manifestFile)
            } else {
                Location.create(project.dir)
            }
            context.report(
                ISSUE,
                location,
                "When targeting Android 14 or higher, you should request `READ_MEDIA_VISUAL_USER_SELECTED` " +
                "alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` to support Selected Photo Access."
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
            implementation = Implementation(SelectedPhotoAccessDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}