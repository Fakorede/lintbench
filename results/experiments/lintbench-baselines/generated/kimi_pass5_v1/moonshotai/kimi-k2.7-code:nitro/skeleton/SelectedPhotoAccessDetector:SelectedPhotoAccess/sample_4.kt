package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlUtils

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"
        private const val ANDROID_NAME = "android:name"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        private const val ANDROID_14_API_LEVEL = 34

        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                On Android 14 (API 34+) the photo access permission model has changed.
                When your app requests `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, users
                can choose to grant access only to selected photos and videos. To
                support this partial access, you should also request
                `READ_MEDIA_VISUAL_USER_SELECTED` and adapt your app to let users
                manage their selection over time. See
                https://developer.android.com/about/versions/14/changes/partial-photo-video-access
                for details.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        if (project.targetSdk < ANDROID_14_API_LEVEL) {
            return
        }

        val manifestFile = project.mergedManifest?.takeIf { it.exists() }
            ?: project.manifestFile?.takeIf { it.exists() }
            ?: return

        val document = XmlUtils.parseDocumentSilently(manifestFile, true) ?: return
        val permissionNodes = document.getElementsByTagName(TAG_USES_PERMISSION)
        val declaredPermissions = permissionNodes.toElementList().mapNotNull { it.name }.toSet()

        if (READ_MEDIA_VISUAL_USER_SELECTED in declaredPermissions) {
            return
        }

        for (element in permissionNodes.toElementList()) {
            val permissionName = element.name ?: continue
            if (permissionName != READ_MEDIA_IMAGES && permissionName != READ_MEDIA_VIDEO) {
                continue
            }

            val line = XmlUtils.getNodeLineNumber(element)
            val location = if (line >= 0) {
                Location.create(manifestFile, line, 0)
            } else {
                Location.create(manifestFile)
            }

            context.report(
                ISSUE,
                location,
                "App targets Android 14+ and declares $permissionName without also " +
                    "declaring $READ_MEDIA_VISUAL_USER_SELECTED. Consider adding " +
                    "$READ_MEDIA_VISUAL_USER_SELECTED to support partial photo library access."
            )
        }
    }

    private fun org.w3c.dom.NodeList.toElementList(): List<org.w3c.dom.Element> {
        val result = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until length) {
            val element = item(i) as? org.w3c.dom.Element ?: continue
            result.add(element)
        }
        return result
    }

    private val org.w3c.dom.Element.name: String?
        get() = getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotBlank() }
            ?: getAttribute(ANDROID_NAME).takeIf { it.isNotBlank() }
            ?: getAttribute(ATTR_NAME).takeIf { it.isNotBlank() }
}