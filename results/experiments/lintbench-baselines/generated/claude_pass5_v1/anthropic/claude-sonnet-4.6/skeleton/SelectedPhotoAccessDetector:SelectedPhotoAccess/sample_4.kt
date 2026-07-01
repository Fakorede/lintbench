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

        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "android:name"
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
            val node = children.item(i)
            if (node is org.w3c.dom.Element && node.tagName == TAG_USES_PERMISSION) {
                val name = node.getAttribute(ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                    permissionNodes[name] = node
                }
            }
        }

        // Check if the app requests READ_MEDIA_IMAGES or READ_MEDIA_VIDEO (or READ_EXTERNAL_STORAGE)
        // but does NOT request READ_MEDIA_VISUAL_USER_SELECTED
        val requestsMediaPermission = permissions.contains(READ_MEDIA_IMAGES) ||
                permissions.contains(READ_MEDIA_VIDEO) ||
                permissions.contains(READ_EXTERNAL_STORAGE)

        val requestsUserSelected = permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)

        if (requestsMediaPermission && !requestsUserSelected) {
            // Find the node to report on
            val reportNode = permissionNodes[READ_MEDIA_IMAGES]
                ?: permissionNodes[READ_MEDIA_VIDEO]
                ?: permissionNodes[READ_EXTERNAL_STORAGE]

            val location = if (reportNode != null) {
                context.getLocation(reportNode)
            } else {
                Location.create(context.file)
            }

            context.report(
                ISSUE,
                location,
                "Should request `READ_MEDIA_VISUAL_USER_SELECTED` to handle partial access to " +
                        "the photo library on Android 14+. See " +
                        "https://developer.android.com/about/versions/14/changes/partial-photo-video-access " +
                        "for more details.",
            )
        }
    }
}