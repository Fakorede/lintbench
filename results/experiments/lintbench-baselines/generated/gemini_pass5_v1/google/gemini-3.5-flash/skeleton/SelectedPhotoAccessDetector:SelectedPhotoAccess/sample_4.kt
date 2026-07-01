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
                On Android 14 (API level 34) and higher, users can grant partial access to their \
                photo library when apps request visual media permissions. If your app requests \
                `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` but does not request \
                `READ_MEDIA_VISUAL_USER_SELECTED`, the system will fall back to a compatibility \
                mode which can lead to a suboptimal user experience.
                
                To support this feature properly, declare and request the `READ_MEDIA_VISUAL_USER_SELECTED` \
                permission alongside the other visual media permissions.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        if (mainProject.targetSdk < 34) {
            return
        }

        val mergedManifest = mainProject.mergedManifest ?: return
        val usesPermissions = mergedManifest.getElementsByTagName("uses-permission")
        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasReadMediaVisualUserSelected = false

        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { item.getAttribute("android:name") }
            if (name == "android.permission.READ_MEDIA_IMAGES") {
                hasReadMediaImages = true
            } else if (name == "android.permission.READ_MEDIA_VIDEO") {
                hasReadMediaVideo = true
            } else if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                hasReadMediaVisualUserSelected = true
            }
        }

        if ((hasReadMediaImages || hasReadMediaVideo) && !hasReadMediaVisualUserSelected) {
            val manifestFile = mainProject.manifestFiles.firstOrNull()
            val location = manifestFile?.let { Location.create(it) }
                ?: Location.create(mainProject.dir)

            context.report(
                ISSUE,
                location,
                "When targeting Android 14 or higher and requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support the Selected Photos Access user experience."
            )
        }
    }
}