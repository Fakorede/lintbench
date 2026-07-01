package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

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
            explanation = "On Android 14 (API level 34) and higher, users can grant partial access to their photo library (Selected Photo Access) when an app requests photo or video permissions. If your app targets Android 14 and requests `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` but does not request `READ_MEDIA_VISUAL_USER_SELECTED`, the system automatically degrades the permission request. To provide a better user experience and handle partial access correctly, you should request `READ_MEDIA_VISUAL_USER_SELECTED` alongside the other media permissions.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val project = context.project
        val targetSdk = try {
            project.targetSdkVersion.apiLevel
        } catch (e: Throwable) {
            project.targetSdk
        }

        if (targetSdk < 34) {
            return
        }

        val mergedManifest: org.w3c.dom.Document = project.mergedManifest ?: return
        val root: org.w3c.dom.Element = mergedManifest.documentElement ?: return
        val usesPermissions = root.getElementsByTagName("uses-permission")

        var hasReadImages = false
        var hasReadVideo = false
        var hasVisualUserSelected = false

        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            var name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name.isEmpty()) {
                name = item.getAttribute("android:name")
            }
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> hasReadImages = true
                "android.permission.READ_MEDIA_VIDEO" -> hasReadVideo = true
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasVisualUserSelected = true
            }
        }

        if ((hasReadImages || hasReadVideo) && !hasVisualUserSelected) {
            val manifestFile = project.manifestFiles.firstOrNull()
            var location = manifestFile?.let { Location.create(it) } ?: Location.create(project.dir)

            if (manifestFile != null) {
                try {
                    val content = manifestFile.readText()
                    val targetPermission = if (hasReadImages) {
                        "android.permission.READ_MEDIA_IMAGES"
                    } else {
                        "android.permission.READ_MEDIA_VIDEO"
                    }
                    val index = content.indexOf(targetPermission)
                    if (index != -1) {
                        location = Location.create(manifestFile, content, index, index + targetPermission.length)
                    }
                } catch (e: Exception) {
                    // Fallback to file-level location
                }
            }

            context.report(
                issue = ISSUE,
                location = location,
                message = "An app targeting Android 14+ that requests `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support Selected Photo Access."
            )
        }
    }
}