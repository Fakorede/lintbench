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
            explanation = "Selected Photo Access is a new ability for users to share partial access to their photo library when apps request access to their device storage on Android 14+. Instead of letting the system manage the selection lifecycle, we recommend you adapt your app to handle partial access to the photo library by checking for the READ_MEDIA_VISUAL_USER_SELECTED permission state or using the Android PhotoPicker.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val permissionName = element.getAttributeNS(ANDROID_URI, "name")
        if (permissionName == READ_MEDIA_IMAGES || permissionName == READ_MEDIA_VIDEO) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "On Android 14+, requesting `$permissionName` may result in partial photo/video access. " +
                "Handle the `READ_MEDIA_VISUAL_USER_SELECTED` state or use the PhotoPicker to ensure a consistent user experience."
            )
        }
    }

    override fun checkMergedProject(context: Context) {
        // Permission checks are handled during individual manifest XML scanning via visitElement.
    }
}