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
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you \
                adapt your app to handle partial access to the photo library.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject

        // Only relevant for apps targeting Android 14+ (API 34+)
        if (mainProject.targetSdk < 34) {
            return
        }

        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val permissions = mutableSetOf<String>()
        val permissionNodes = mutableMapOf<String, org.w3c.dom.Element>()

        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            if (child.tagName == "uses-permission") {
                val name = child.getAttributeNS(
                    "http://schemas.android.com/apk/res/android",
                    "name"
                ).ifEmpty {
                    child.getAttribute("android:name")
                }
                if (name.isNotEmpty()) {
                    permissions.add(name)
                    permissionNodes[name] = child
                }
            }
        }

        val requestsMediaImages = permissions.contains(READ_MEDIA_IMAGES)
        val requestsMediaVideo = permissions.contains(READ_MEDIA_VIDEO)
        val requestsExternalStorage = permissions.contains(READ_EXTERNAL_STORAGE)
        val requestsUserSelected = permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)

        val requestsMediaAccess = requestsMediaImages || requestsMediaVideo || requestsExternalStorage

        if (requestsMediaAccess && !requestsUserSelected) {
            context.report(
                ISSUE,
                Location.create(context.mainProject.dir),
                "When targeting Android 14+, consider adding the " +
                    "`READ_MEDIA_VISUAL_USER_SELECTED` permission to handle partial " +
                    "access to the photo library (Selected Photo Access). This allows " +
                    "users to grant access to a subset of their photos and videos rather " +
                    "than all media files."
            )
        }
    }
}