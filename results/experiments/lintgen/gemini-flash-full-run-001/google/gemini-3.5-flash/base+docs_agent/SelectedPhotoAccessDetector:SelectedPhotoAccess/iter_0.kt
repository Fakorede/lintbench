package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != SdkConstants.TAG_MANIFEST) return

        val permissionElements = mutableListOf<Element>()
        var hasImagesOrVideo = false
        var hasVisualUserSelected = false

        val tags = listOf(
            SdkConstants.TAG_USES_PERMISSION,
            SdkConstants.TAG_USES_PERMISSION_SDK_23,
            "uses-permission-sdk-m"
        )

        for (tag in tags) {
            val list = root.getElementsByTagName(tag)
            for (i in 0 until list.length) {
                val element = list.item(i) as? Element ?: continue
                val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == PERMISSION_IMAGES || name == PERMISSION_VIDEO) {
                    hasImagesOrVideo = true
                    permissionElements.add(element)
                } else if (name == PERMISSION_VISUAL_USER_SELECTED) {
                    hasVisualUserSelected = true
                }
            }
        }

        if (hasImagesOrVideo && !hasVisualUserSelected) {
            for (element in permissionElements) {
                val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "When requesting `$name`, you should also request `$PERMISSION_VISUAL_USER_SELECTED` to support the user-selected photos feature on Android 14 and higher"
                )
            }
        }
    }

    companion object {
        private const val PERMISSION_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val PERMISSION_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Missing READ_MEDIA_VISUAL_USER_SELECTED permission",
            explanation = """
                On Android 14 (API level 34) and higher, users can grant partial access to their photo library \
                when apps request access to device storage. To support this behavior and provide a better user \
                experience, you should declare the `READ_MEDIA_VISUAL_USER_SELECTED` permission in your manifest \
                alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.
            """,
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