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

        val usesPermissionNodes = root.getElementsByTagName("uses-permission")

        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasReadExternalStorage = false
        var hasReadMediaVisualUserSelected = false

        for (i in 0 until usesPermissionNodes.length) {
            val node = usesPermissionNodes.item(i)
            val permissionName = node.attributes?.getNamedItemNS(
                "http://schemas.android.com/apk/res/android", "name"
            )?.nodeValue ?: continue

            when (permissionName) {
                READ_MEDIA_IMAGES -> hasReadMediaImages = true
                READ_MEDIA_VIDEO -> hasReadMediaVideo = true
                READ_EXTERNAL_STORAGE -> hasReadExternalStorage = true
                READ_MEDIA_VISUAL_USER_SELECTED -> hasReadMediaVisualUserSelected = true
            }
        }

        val requestsPhotoAccess = hasReadMediaImages || hasReadMediaVideo || hasReadExternalStorage

        if (requestsPhotoAccess && !hasReadMediaVisualUserSelected) {
            context.report(
                issue = ISSUE,
                location = Location.create(context.mainProject.dir),
                message = "Your app is requesting photo library access but does not handle " +
                    "`READ_MEDIA_VISUAL_USER_SELECTED`. On Android 14+, users can grant " +
                    "partial access to their photo library. Add " +
                    "`READ_MEDIA_VISUAL_USER_SELECTED` permission and update your app to " +
                    "handle partial photo library access.",
            )
        }
    }
}