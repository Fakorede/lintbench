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
        if (context.file.name != SdkConstants.FN_ANDROID_MANIFEST_XML) {
            return
        }

        val targetSdk = context.project.targetSdk
        if (targetSdk < 34) {
            return
        }

        val root = document.documentElement ?: return
        val permissions = root.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)
        
        var hasReadImages = false
        var hasReadVideo = false
        var hasVisualUserSelected = false

        for (i in 0 until permissions.length) {
            val item = permissions.item(i) as? Element ?: continue
            val name = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            when (name) {
                "android.permission.READ_MEDIA_IMAGES" -> hasReadImages = true
                "android.permission.READ_MEDIA_VIDEO" -> hasReadVideo = true
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> hasVisualUserSelected = true
            }
        }

        if ((hasReadImages || hasReadVideo) && !hasVisualUserSelected) {
            for (i in 0 until permissions.length) {
                val item = permissions.item(i) as? Element ?: continue
                val name = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "android.permission.READ_MEDIA_IMAGES" || name == "android.permission.READ_MEDIA_VIDEO") {
                    context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "When targeting Android 14+, you should request `READ_MEDIA_VISUAL_USER_SELECTED` alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` to support Selected Photo Access."
                    )
                    break
                }
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
                your app to handle partial access to the photo library by requesting the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.
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