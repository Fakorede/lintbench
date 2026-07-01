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
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    private var hasSelectedPermission = false
    private val mediaPermissionNodes = mutableListOf<Element>()

    override fun beforeCheckFile(context: XmlContext) {
        hasSelectedPermission = false
        mediaPermissionNodes.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
            hasSelectedPermission = true
        } else if (name == "android.permission.READ_MEDIA_IMAGES" || name == "android.permission.READ_MEDIA_VIDEO") {
            mediaPermissionNodes.add(element)
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk >= 34 && !hasSelectedPermission && mediaPermissionNodes.isNotEmpty()) {
            for (node in mediaPermissionNodes) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "When targeting Android 14 or higher, you should request `READ_MEDIA_VISUAL_USER_SELECTED` " +
                            "alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` to support Selected Photo Access."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Missing Selected Photo Access permission",
            explanation = """
                On Android 14 (API 34) and above, users can grant partial access to their photo library. \
                To support this behavior change and provide a better user experience, your app should request \
                the `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.
            """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}