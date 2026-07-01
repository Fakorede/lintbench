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
            explanation = """
                On Android 14 (API level 34) or higher, users can grant partial access to their \
                photo library (Selected Photo Access) when an app requests `READ_MEDIA_IMAGES` or \
                `READ_MEDIA_VIDEO`. To provide a better user experience and support this partial \
                access lifecycle, you should also request the `READ_MEDIA_VISUAL_USER_SELECTED` permission.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        if (project.targetSdkVersion.featureLevel < 34) {
            return
        }

        val mergedManifest = project.mergedManifest ?: return
        val usesPermissions = mergedManifest.getElementsByTagName("uses-permission")
        var hasImages = false
        var hasVideo = false
        var hasSelected = false

        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            var name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name.isEmpty()) {
                name = item.getAttribute("android:name")
            }
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> hasImages = true
                "android.permission.READ_MEDIA_VIDEO" -> hasVideo = true
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasSelected = true
            }
        }

        if ((hasImages || hasVideo) && !hasSelected) {
            val manifestFile = project.manifestFiles.firstOrNull()
            val location = if (manifestFile != null) {
                Location.create(manifestFile)
            } else {
                Location.create(project.dir)
            }

            context.report(
                ISSUE,
                location,
                "When targeting Android 14 or higher and requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support the user-selected media access flow."
            )
        }
    }
}