package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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

    override fun getApplicableElements(): Collection<String>? = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.targetSdk < 34) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != "android.permission.READ_MEDIA_IMAGES" && name != "android.permission.READ_MEDIA_VIDEO") {
            return
        }

        val reportedKey = "SelectedPhotoAccessReported"
        if (context.clientData[reportedKey] == true) return

        val document = element.ownerDocument
        val permissions = document.getElementsByTagName("uses-permission")
        var hasUserSelected = false

        for (i in 0 until permissions.length) {
            val perm = permissions.item(i) as Element
            if (perm.getAttributeNS(ANDROID_URI, ATTR_NAME) == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                hasUserSelected = true
                break
            }
        }

        if (!hasUserSelected) {
            context.clientData[reportedKey] = true
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "When targeting Android 14+, apps requesting photo/video access should handle partial access by declaring `READ_MEDIA_VISUAL_USER_SELECTED`."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt your app to handle partial access to the photo library by declaring the `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
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
}