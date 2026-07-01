package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    private var hasVisualUserSelected = false
    private val targets = mutableListOf<Pair<Element, Location>>()

    override fun beforeCheckFile(context: Context) {
        hasVisualUserSelected = false
        targets.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
            hasVisualUserSelected = true
        } else if (name == "android.permission.READ_MEDIA_IMAGES" || name == "android.permission.READ_MEDIA_VIDEO") {
            targets.add(element to context.getLocation(element))
        }
    }

    override fun afterCheckFile(context: Context) {
        if (!hasVisualUserSelected && targets.isNotEmpty()) {
            for ((_, location) in targets) {
                context.report(
                    ISSUE,
                    location,
                    "When requesting photo or video access on Android 14+, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support Selected Photo Access."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Missing READ_MEDIA_VISUAL_USER_SELECTED permission for partial photo library access",
            explanation = """
                Android 14 introduces Selected Photo Access, which allows users to grant partial access to their photo library. \
                To support this, when your app requests `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, you should also request \
                `READ_MEDIA_VISUAL_USER_SELECTED` to provide a consistent user experience.
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