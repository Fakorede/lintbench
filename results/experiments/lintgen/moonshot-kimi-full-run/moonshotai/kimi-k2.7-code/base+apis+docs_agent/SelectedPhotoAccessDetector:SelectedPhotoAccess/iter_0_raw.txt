package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.NODE_USES_PERMISSION
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

    override fun getApplicableElements(): Collection<String> = listOf(NODE_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permission !in PHOTO_LIBRARY_PERMISSIONS) {
            return
        }

        if (context.project.targetSdk >= ANDROID_14_API) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "On Android 14+ (API 34), users can grant only partial access to their photo library. "
                        + "If your app requests `$permission`, you should adapt it to handle partial access. "
                        + "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    companion object {
        private const val ANDROID_14_API = 34

        private val PHOTO_LIBRARY_PERMISSIONS = setOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO"
        )

        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo
                library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, adapt your app to handle
                partial access to the photo library. See the Android 14 documentation for details.
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