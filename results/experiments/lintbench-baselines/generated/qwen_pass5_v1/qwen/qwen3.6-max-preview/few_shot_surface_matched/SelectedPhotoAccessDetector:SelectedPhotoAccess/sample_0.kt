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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        val permissionName = attribute.value
        if (permissionName == "android.permission.READ_MEDIA_IMAGES" ||
            permissionName == "android.permission.READ_MEDIA_VIDEO") {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Selected Photo Access is a new ability for users to share partial access to their photo library " +
                "when apps request access to their device storage on Android 14+. " +
                "Instead of letting the system manage the selection lifecycle, we recommend you adapt " +
                "your app to handle partial access to the photo library."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Selected Photo Access",
            explanation = "Selected Photo Access is a new ability for users to share partial access to their photo library " +
                "when apps request access to their device storage on Android 14+. " +
                "Instead of letting the system manage the selection lifecycle, we recommend you adapt " +
                "your app to handle partial access to the photo library.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(SelectedPhotoAccessDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}