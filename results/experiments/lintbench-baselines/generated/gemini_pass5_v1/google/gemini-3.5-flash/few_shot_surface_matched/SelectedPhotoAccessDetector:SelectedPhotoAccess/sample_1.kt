package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        if (project.targetSdkVersion.apiLevel < 34) {
            return
        }

        val mergedManifest = project.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasReadMediaVisualUserSelected = false

        val nl = root.getElementsByTagName("uses-permission")
        for (i in 0 until nl.length) {
            val item = nl.item(i) as? org.w3c.dom.Element ?: continue
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
            val location = manifestFile?.let { context.getLocation(it) } ?: context.getLocation(project.dir)
            context.report(
                ISSUE,
                location,
                "When targeting Android 14 or higher and requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, " +
                        "you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support the Selected Photo Access " +
                        "user experience."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                your app to handle partial access to the photo library.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(SelectedPhotoAccessDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}