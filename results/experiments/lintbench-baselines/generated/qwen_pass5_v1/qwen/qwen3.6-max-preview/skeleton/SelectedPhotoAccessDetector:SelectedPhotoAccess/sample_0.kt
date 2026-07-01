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

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "Android 14 introduces Selected Photo Access, allowing users to grant partial access to their photo library. When requesting READ_MEDIA_IMAGES or READ_MEDIA_VIDEO, you should also declare READ_MEDIA_VISUAL_USER_SELECTED to properly handle partial access, avoid unexpected permission revocations, and provide a better user experience.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val PERM_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERM_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val PERM_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isManifestFile()) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != PERM_READ_MEDIA_IMAGES && name != PERM_READ_MEDIA_VIDEO) return

        val document = context.document
        val permissions = document.getElementsByTagName(TAG_USES_PERMISSION)
        var hasVisualUserSelected = false

        for (i in 0 until permissions.length) {
            val permElement = permissions.item(i) as? Element ?: continue
            if (permElement.getAttributeNS(ANDROID_URI, ATTR_NAME) == PERM_VISUAL_USER_SELECTED) {
                hasVisualUserSelected = true
                break
            }
        }

        if (!hasVisualUserSelected) {
            context.report(
                issue = ISSUE,
                location = context.getLocation(element),
                message = "Requesting `$name` without `$PERM_VISUAL_USER_SELECTED`. Android 14+ supports partial photo library access. Declare `$PERM_VISUAL_USER_SELECTED` to handle selected photo access properly."
            )
        }
    }
}