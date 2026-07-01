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

    private var hasReadMediaImages: Boolean = false
    private var hasReadMediaVideo: Boolean = false
    private var hasReadMediaVisualUserSelected: Boolean = false

    override fun beforeCheckRootProject(context: Context) {
        hasReadMediaImages = false
        hasReadMediaVideo = false
        hasReadMediaVisualUserSelected = false
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        when (name) {
            PERMISSION_READ_MEDIA_IMAGES -> hasReadMediaImages = true
            PERMISSION_READ_MEDIA_VIDEO -> hasReadMediaVideo = true
            PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED -> hasReadMediaVisualUserSelected = true
        }
    }

    override fun checkMergedProject(context: Context) {
        val requestsMediaPermissions = hasReadMediaImages || hasReadMediaVideo
        if (requestsMediaPermissions && !hasReadMediaVisualUserSelected) {
            val mergedManifest = context.mainProject.mergedManifest ?: return
            val root = mergedManifest.documentElement ?: return

            // Find the first READ_MEDIA_IMAGES or READ_MEDIA_VIDEO permission element to report on
            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node is Element && node.tagName == TAG_USES_PERMISSION) {
                    val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == PERMISSION_READ_MEDIA_IMAGES || name == PERMISSION_READ_MEDIA_VIDEO) {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Your app is requesting `READ_MEDIA_IMAGES` and/or `READ_MEDIA_VIDEO` " +
                                "but is not handling the new `READ_MEDIA_VISUAL_USER_SELECTED` " +
                                "permission introduced in Android 14 for partial photo library " +
                                "access. We recommend you update your app to handle partial access " +
                                "to the photo library. " +
                                "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
                        )
                        return
                    }
                }
            }

            // Fallback: report on the root element if no specific node found
            context.report(
                ISSUE,
                context.getLocation(root),
                "Your app is requesting `READ_MEDIA_IMAGES` and/or `READ_MEDIA_VIDEO` " +
                    "but is not handling the new `READ_MEDIA_VISUAL_USER_SELECTED` " +
                    "permission introduced in Android 14 for partial photo library access. " +
                    "We recommend you update your app to handle partial access to the photo library. " +
                    "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    companion object {
        private const val PERMISSION_READ_MEDIA_IMAGES =
            "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_READ_MEDIA_VIDEO =
            "android.permission.READ_MEDIA_VIDEO"
        private const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation =
                "Selected Photo Access is a new ability for users to share partial access " +
                    "to their photo library when apps request access to their device storage " +
                    "on Android 14+.\n\n" +
                    "Instead of letting the system manage the selection lifecycle, we recommend " +
                    "you adapt your app to handle partial access to the photo library by also " +
                    "requesting the `READ_MEDIA_VISUAL_USER_SELECTED` permission and handling " +
                    "the case where the user grants only partial access.\n\n" +
                    "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true
        )
    }
}