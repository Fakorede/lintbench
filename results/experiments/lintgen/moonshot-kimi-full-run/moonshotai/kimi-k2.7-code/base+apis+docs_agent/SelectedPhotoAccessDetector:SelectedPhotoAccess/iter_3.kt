package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permission != READ_MEDIA_IMAGES && permission != READ_MEDIA_VIDEO) {
            return
        }

        if (context.project.targetSdk >= ANDROID_14_API) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "On Android 14+ (API 34), users can grant only partial access to their photo library. " +
                        "If your app requests `$permission`, you should adapt it to handle partial access. " +
                        "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    companion object {
        private const val ANDROID_14_API = 34
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"

        @JvmField
        val ISSUE = Issue.create(
            "SelectedPhotoAccess",
            "Behavior change when requesting photo library access",
            """
                Selected Photo Access is a new ability for users to share partial access to their photo
                library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, adapt your app to handle
                partial access to the photo library. See the Android 14 documentation for details.
            """.trimIndent(),
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }
}