package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "Selected Photo Access is a new ability for users to share partial access to their photo library when apps request access to their device storage on Android 14+.\n\n" +
                    "Instead of letting the system manage the selection lifecycle, we recommend you adapt your app to handle partial access to the photo library.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val PERM_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERM_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(ANDROID_URI, "name")
        if (permissionName == PERM_READ_MEDIA_IMAGES || permissionName == PERM_READ_MEDIA_VIDEO) {
            if (context.project.targetSdkVersion >= 34) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "When targeting Android 14+, requesting `$permissionName` allows users to grant partial access. " +
                            "Consider also requesting `android.permission.READ_MEDIA_VISUAL_USER_SELECTED` to handle the new selection lifecycle explicitly."
                )
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        // Detection is handled via XML scanning of individual manifest files.
    }
}