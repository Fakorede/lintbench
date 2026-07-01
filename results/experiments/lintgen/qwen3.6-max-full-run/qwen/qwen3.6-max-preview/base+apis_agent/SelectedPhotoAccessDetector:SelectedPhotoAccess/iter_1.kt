package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdkLevel = context.project.targetSdkVersion?.apiLevel ?: 0
        if (targetSdkLevel < 34) return

        val permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (permission == "android.permission.READ_MEDIA_IMAGES" ||
            permission == "android.permission.READ_MEDIA_VIDEO" ||
            permission == "android.permission.READ_EXTERNAL_STORAGE") {

            context.report(
                ISSUE,
                context.getNameLocation(element),
                "On Android 14+ (API 34+), users can grant partial access to their photo library. " +
                "Consider adapting your app to handle selected photo access instead of relying on full library access."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt your app \
                to handle partial access to the photo library.

                Reference documentation:
                https://developer.android.com/about/versions/14/changes/partial-photo-video-access
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