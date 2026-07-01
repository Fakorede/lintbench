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

    private var hasMediaImages = false
    private var hasMediaVideo = false
    private var hasVisualUserSelected = false
    private var mainPermissionElement: Element? = null

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-permission")
    }

    override fun beforeCheckFile(context: XmlContext) {
        hasMediaImages = false
        hasMediaVideo = false
        hasVisualUserSelected = false
        mainPermissionElement = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        when (name) {
            "android.permission.READ_MEDIA_IMAGES" -> {
                hasMediaImages = true
                if (mainPermissionElement == null) {
                    mainPermissionElement = element
                }
            }
            "android.permission.READ_MEDIA_VIDEO" -> {
                hasMediaVideo = true
                if (mainPermissionElement == null) {
                    mainPermissionElement = element
                }
            }
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> {
                hasVisualUserSelected = true
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk >= 34 && (hasMediaImages || hasMediaVideo) && !hasVisualUserSelected) {
            val element = mainPermissionElement ?: return
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Apps targeting Android 14 or higher that request `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` should also declare `READ_MEDIA_VISUAL_USER_SELECTED` to support the Selected Photos Access user experience."
            )
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