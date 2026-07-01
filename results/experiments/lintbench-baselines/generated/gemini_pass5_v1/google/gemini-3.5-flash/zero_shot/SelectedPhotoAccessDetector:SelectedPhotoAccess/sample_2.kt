package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), Detector.XmlScanner {

    private var hasReadMediaImages = false
    private var hasReadMediaVideo = false
    private var hasVisualUserSelected = false
    private val permissionsToReport = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-permission")
    }

    override fun beforeCheckFile(context: XmlContext) {
        hasReadMediaImages = false
        hasReadMediaVideo = false
        hasVisualUserSelected = false
        permissionsToReport.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        when (name) {
            "android.permission.READ_MEDIA_IMAGES" -> {
                hasReadMediaImages = true
                permissionsToReport.add(element)
            }
            "android.permission.READ_MEDIA_VIDEO" -> {
                hasReadMediaVideo = true
                permissionsToReport.add(element)
            }
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> {
                hasVisualUserSelected = true
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        val targetSdk = context.project.targetSdkVersion
        if (targetSdk.apiLevel >= 34) {
            if ((hasReadMediaImages || hasReadMediaVideo) && !hasVisualUserSelected) {
                for (element in permissionsToReport) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "When targeting Android 14 or higher and requesting visual media permissions, " +
                                "you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support the " +
                                "Selected Photos Access feature."
                    )
                }
            }
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
                
                Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                your app to handle partial access to the photo library by requesting the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.
            """,
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