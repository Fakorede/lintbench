package com.android.tools.lint.checks

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
import com.android.SdkConstants

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val targetSdkVersion = context.project.targetSdkVersion
        if (targetSdkVersion.apiLevel < 34) {
            return
        }

        val root = document.documentElement ?: return
        val permissionsToWarn = mutableListOf<Element>()
        var hasImages = false
        var hasVideo = false
        var hasSelected = false

        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == SdkConstants.TAG_USES_PERMISSION) {
                val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                when (name) {
                    "android.permission.READ_MEDIA_IMAGES" -> {
                        hasImages = true
                        permissionsToWarn.add(child)
                    }
                    "android.permission.READ_MEDIA_VIDEO" -> {
                        hasVideo = true
                        permissionsToWarn.add(child)
                    }
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> {
                        hasSelected = true
                    }
                }
            }
        }

        if ((hasImages || hasVideo) && !hasSelected) {
            for (permission in permissionsToWarn) {
                val location = context.getNameLocation(permission)
                context.report(
                    ISSUE,
                    permission,
                    location,
                    "When requesting photo or video access on Android 14+, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to allow users to select specific photos/videos."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                your app to handle partial access to the photo library.
            """.trimIndent(),
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