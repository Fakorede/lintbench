package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
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

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        var child = root.firstChild
        while (child != null) {
            if (child is Element && child.tagName == TAG_USES_PERMISSION) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") {
                    // Already handling selected photo access
                    return
                }
            }
            child = child.nextSibling
        }

        var hasReadMediaImages = false
        var hasReadMediaVideo = false
        var readMediaImagesElement: Element? = null

        child = root.firstChild
        while (child != null) {
            if (child is Element && child.tagName == TAG_USES_PERMISSION) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                when (name) {
                    "android.permission.READ_MEDIA_IMAGES" -> {
                        hasReadMediaImages = true
                        readMediaImagesElement = child
                    }
                    "android.permission.READ_MEDIA_VIDEO" -> {
                        hasReadMediaVideo = true
                    }
                }
            }
            child = child.nextSibling
        }

        if (hasReadMediaImages || hasReadMediaVideo) {
            val targetElement = readMediaImagesElement ?: run {
                // find READ_MEDIA_VIDEO element
                var el: Element? = null
                var c = root.firstChild
                while (c != null) {
                    if (c is Element && c.tagName == TAG_USES_PERMISSION) {
                        val name = c.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (name == "android.permission.READ_MEDIA_VIDEO") {
                            el = c
                            break
                        }
                    }
                    c = c.nextSibling
                }
                el
            } ?: return

            // We need an XmlContext to report, but checkMergedProject only gives us a Context.
            // Report on the merged manifest document location via the context directly.
            context.report(
                ISSUE,
                context.getLocation(targetElement),
                "Your app is requesting `READ_MEDIA_IMAGES` and/or `READ_MEDIA_VIDEO` but is " +
                    "not handling the `READ_MEDIA_VISUAL_USER_SELECTED` permission introduced in " +
                    "Android 14. On Android 14+, users can grant partial access to their photo " +
                    "library. We recommend you add `READ_MEDIA_VISUAL_USER_SELECTED` and adapt " +
                    "your app to handle partial photo library access. See " +
                    "https://developer.android.com/about/versions/14/changes/partial-photo-video-access " +
                    "for details."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation =
                """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you \
                adapt your app to handle partial access to the photo library.

                To do this, declare the `READ_MEDIA_VISUAL_USER_SELECTED` permission in your \
                manifest and update your permission-request logic to handle the case where the \
                user grants only partial access.

                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access \
                for more details.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo =
                "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }
}