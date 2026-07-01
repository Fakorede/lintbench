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

        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation =
                """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you \
                adapt your app to handle partial access to the photo library.

                To do this, declare the `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside \
                `READ_MEDIA_IMAGES` and/or `READ_MEDIA_VIDEO` in your manifest. This allows your \
                app to gracefully handle the case where users grant only partial access to their \
                photo library.

                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access \
                for more details.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasReadMediaVisualUserSelected = false

        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName == "uses-permission") {
                val nameAttr = child.attributes?.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android",
                    "name"
                ) ?: child.attributes?.getNamedItem("android:name")
                val permissionName = nameAttr?.nodeValue ?: continue
                when (permissionName) {
                    READ_MEDIA_IMAGES -> hasReadMediaImages = true
                    READ_MEDIA_VIDEO -> hasReadMediaVideo = true
                    READ_MEDIA_VISUAL_USER_SELECTED -> hasReadMediaVisualUserSelected = true
                }
            }
        }

        val requestsMediaPermission = hasReadMediaImages || hasReadMediaVideo
        if (requestsMediaPermission && !hasReadMediaVisualUserSelected) {
            context.report(
                issue = ISSUE,
                location = Location.create(context.mainProject.dir),
                message =
                    "Your app requests `READ_MEDIA_IMAGES` and/or `READ_MEDIA_VIDEO` but does " +
                        "not request `READ_MEDIA_VISUAL_USER_SELECTED`. On Android 14+, users " +
                        "can grant partial access to their photo library. To handle this " +
                        "gracefully, declare the `READ_MEDIA_VISUAL_USER_SELECTED` permission " +
                        "in your manifest.",
            )
        }
    }
}