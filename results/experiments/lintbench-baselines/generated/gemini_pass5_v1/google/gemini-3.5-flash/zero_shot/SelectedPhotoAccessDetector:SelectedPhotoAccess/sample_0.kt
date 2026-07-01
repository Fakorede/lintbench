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

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val usesPermissions = root.getElementsByTagName("uses-permission")
        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var hasVisualUserSelected = false
        var targetElement: Element? = null

        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> {
                    hasReadMediaImages = true
                    targetElement = item
                }
                "android.permission.READ_MEDIA_VIDEO" -> {
                    hasReadMediaVideo = true
                    targetElement = item
                }
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> {
                    hasVisualUserSelected = true
                }
            }
        }

        if ((hasReadMediaImages || hasReadMediaVideo) && !hasVisualUserSelected) {
            val targetSdkVersion = context.project.targetSdkVersion.featureLevel
            if (targetSdkVersion >= 34) {
                val location = context.getNameLocation(targetElement ?: root)
                context.report(
                    ISSUE,
                    location,
                    "When targeting Android 14 or higher and requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support Selected Photo Access."
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
                Selected Photo Access is a new ability for users to share partial access to their photo \
                library when apps request access to their device storage on Android 14+.

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