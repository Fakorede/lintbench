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

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "On Android 14 (API level 34) and higher, users can grant partial access to their photo library when an app requests READ_MEDIA_IMAGES or READ_MEDIA_VIDEO. If your app requests these permissions but does not declare the READ_MEDIA_VISUAL_USER_SELECTED permission, the system runs your app in a compatibility mode where it repeatedly prompts the user for access, which can lead to a poor user experience. Declare READ_MEDIA_VISUAL_USER_SELECTED in your manifest and handle the partial access lifecycle to provide a better experience.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        if (project.targetSdkVersion.apiLevel < 34) {
            return
        }

        val document = project.mergedManifest ?: return
        val root = document.documentElement ?: return
        val usesPermissions = root.getElementsByTagName("uses-permission")
        var hasReadImages = false
        var hasReadVideo = false
        var hasSelectedPhoto = false

        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { element.getAttribute("android:name") }
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> hasReadImages = true
                "android.permission.READ_MEDIA_VIDEO" -> hasReadVideo = true
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasSelectedPhoto = true
            }
        }

        if ((hasReadImages || hasReadVideo) && !hasSelectedPhoto) {
            val manifestFile = project.manifestFiles.firstOrNull()
            val location = if (manifestFile != null) {
                Location.create(manifestFile)
            } else {
                Location.create(project.dir)
            }

            context.report(
                ISSUE,
                location,
                "The app requests photo or video access but does not declare `READ_MEDIA_VISUAL_USER_SELECTED` which is required for Selected Photo Access on Android 14+."
            )
        }
    }
}