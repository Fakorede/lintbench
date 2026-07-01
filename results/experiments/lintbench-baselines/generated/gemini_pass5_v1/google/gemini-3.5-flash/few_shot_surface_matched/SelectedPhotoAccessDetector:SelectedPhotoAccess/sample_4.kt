package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val project = context.project
        val targetSdk = project.targetSdkVersion?.apiLevel ?: 0
        if (targetSdk < 34) return

        val manifest = project.mergedManifest ?: return
        val root = manifest.documentElement ?: return

        val usesPermissions = root.getElementsByTagName("uses-permission")
        var hasImages = false
        var hasVideo = false
        var hasVisualUserSelected = false

        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.permission.READ_MEDIA_IMAGES") {
                hasImages = true
            } else if (name == "android.permission.READ_MEDIA_VIDEO") {
                hasVideo = true
            } else if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                hasVisualUserSelected = true
            }
        }

        if ((hasImages || hasVideo) && !hasVisualUserSelected) {
            val mainManifest = project.manifestFiles.firstOrNull() ?: project.dir
            val location = Location.create(mainManifest)

            context.report(
                ISSUE,
                location,
                "When targeting Android 14 or higher and requesting READ_MEDIA_IMAGES or " +
                "READ_MEDIA_VIDEO, you should also request READ_MEDIA_VISUAL_USER_SELECTED " +
                "to support the user-selected photos access feature."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Missing READ_MEDIA_VISUAL_USER_SELECTED permission",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo \
                library when apps request access to their device storage on Android 14+.
                
                Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                your app to handle partial access to the photo library. To do so, request \
                READ_MEDIA_VISUAL_USER_SELECTED alongside READ_MEDIA_IMAGES or READ_MEDIA_VIDEO.
            """.trimIndent(),
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