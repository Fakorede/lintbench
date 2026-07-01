package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.Location

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
            explanation = """
                Android 14 (API level 34) introduces Selected Photo Access, which allows users to \
                grant apps access to only selected photos and videos rather than the entire library.
                
                If your app targets Android 14 or higher and requests `READ_MEDIA_IMAGES` or \
                `READ_MEDIA_VIDEO`, you should also declare the `READ_MEDIA_VISUAL_USER_SELECTED` \
                permission to handle the partial photo library access correctly.
            """.trimIndent(),
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

        val manifest = project.mergedManifest ?: return
        val permissions = manifest.getElementsByTagName("uses-permission")
        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasSelectedPhotoAccess = false

        for (i in 0 until permissions.length) {
            val item = permissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { item.getAttribute("android:name") }
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> hasReadMediaImages = true
                "android.permission.READ_MEDIA_VIDEO" -> hasReadMediaVideo = true
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasSelectedPhotoAccess = true
            }
        }

        if ((hasReadMediaImages || hasReadMediaVideo) && !hasSelectedPhotoAccess) {
            val manifestFile = project.manifestFiles.firstOrNull()
            val location = if (manifestFile != null) {
                Location.create(manifestFile)
            } else {
                Location.create(context.file)
            }

            context.report(
                issue = ISSUE,
                location = location,
                message = "When targeting Android 14 or higher, declaring `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` also requires declaring `READ_MEDIA_VISUAL_USER_SELECTED` to support user-selected media access."
            )
        }
    }
}