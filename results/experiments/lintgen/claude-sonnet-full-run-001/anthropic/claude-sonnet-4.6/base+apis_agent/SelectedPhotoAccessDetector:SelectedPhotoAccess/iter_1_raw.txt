package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

/**
 * Detector that warns when apps request photo/storage permissions without
 * handling the new Selected Photo Access (partial photo library access)
 * introduced in Android 14.
 */
class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {

        // Permissions related to photo/storage access
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

        // The new partial access permission introduced in Android 14
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private val PHOTO_PERMISSIONS = setOf(
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO,
            READ_EXTERNAL_STORAGE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access \
                to their photo library when apps request access to their device storage \
                on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library.

                To support Selected Photo Access, you should also request the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside \
                `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`. This allows users to grant \
                access to a subset of their photo library rather than all media files.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            ),
            androidSpecific = true
        ).addMoreInfo(
            "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )

        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(ANDROID_NS, ATTR_NAME) ?: return

        if (permissionName !in PHOTO_PERMISSIONS) return

        // Check if the manifest already declares READ_MEDIA_VISUAL_USER_SELECTED
        val document = element.ownerDocument ?: return
        val allUsesPermissions = document.getElementsByTagName(TAG_USES_PERMISSION)

        var hasVisualUserSelected = false
        for (i in 0 until allUsesPermissions.length) {
            val node = allUsesPermissions.item(i) as? Element ?: continue
            val name = node.getAttributeNS(ANDROID_NS, ATTR_NAME)
            if (name == READ_MEDIA_VISUAL_USER_SELECTED) {
                hasVisualUserSelected = true
                break
            }
        }

        if (!hasVisualUserSelected) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(element),
                message = "When requesting `$permissionName`, you should also add " +
                        "`READ_MEDIA_VISUAL_USER_SELECTED` to support Selected Photo Access " +
                        "on Android 14+. This allows users to grant partial access to their " +
                        "photo library."
            )
        }
    }
}