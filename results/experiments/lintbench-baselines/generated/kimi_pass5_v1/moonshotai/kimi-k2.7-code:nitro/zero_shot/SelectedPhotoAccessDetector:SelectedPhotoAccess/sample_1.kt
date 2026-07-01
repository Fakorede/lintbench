package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), Detector.XmlScanner {

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"
        private const val MIN_TARGET_SDK = 34

        private const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        private val PHOTO_ACCESS_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                On Android 14+ (API 34+), users can grant an app partial access to their
                photo library when it requests READ_MEDIA_IMAGES or READ_MEDIA_VIDEO.

                To properly support this Selected Photo Access behavior, your app should
                declare READ_MEDIA_VISUAL_USER_SELECTED and handle partial access itself
                instead of relying on the system to manage the selection lifecycle.

                For more information, see
                https://developer.android.com/about/versions/14/changes/partial-photo-video-access.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.mainProject.targetSdkVersion.apiLevel < MIN_TARGET_SDK) {
            return
        }

        val usesPermissions = document.getElementsByTagName(TAG_USES_PERMISSION)
        var hasSelectedPermission = false
        val candidates = mutableListOf<Element>()

        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME)
            when (name) {
                PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED -> hasSelectedPermission = true
                in PHOTO_ACCESS_PERMISSIONS -> candidates.add(element)
            }
        }

        if (hasSelectedPermission) {
            return
        }

        for (element in candidates) {
            val permission = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME)
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "App targets Android 14+ and declares $permission without " +
                    "READ_MEDIA_VISUAL_USER_SELECTED. Declare the selected-photo " +
                    "permission and handle partial photo/video access."
            )
        }
    }
}