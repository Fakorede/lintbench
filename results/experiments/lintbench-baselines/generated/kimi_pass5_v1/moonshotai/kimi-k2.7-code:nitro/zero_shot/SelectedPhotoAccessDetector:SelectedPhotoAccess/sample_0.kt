package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
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

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (permission !in AFFECTED_PERMISSIONS) return
        if (context.mainProject.targetSdk < 34) return

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "On Android 14+ (targetSdk 34+) requesting $permission is subject to Selected Photo Access. " +
                    "Consider using the photo picker, or request " +
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED and handle partial access."
        )
    }

    companion object {
        private val AFFECTED_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access on Android 14+ lets users share only a subset of their \
                photo library with your app. When you request READ_MEDIA_IMAGES or \
                READ_MEDIA_VIDEO, the system may grant access only to the user's selection \
                and may revoke that access later.

                Instead of relying on broad media permissions, prefer using the photo picker, \
                or request READ_MEDIA_VISUAL_USER_SELECTED and adapt your app to gracefully \
                handle partial access to the photo library.
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