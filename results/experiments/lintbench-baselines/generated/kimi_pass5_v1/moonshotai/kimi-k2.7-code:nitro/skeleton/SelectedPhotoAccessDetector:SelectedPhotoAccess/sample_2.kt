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
import org.w3c.dom.Attr
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val ATTR_TARGET_SDK_VERSION = "targetSdkVersion"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_USES_SDK = "uses-sdk"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val ANDROID_14_API_LEVEL = 34

        private const val ISSUE_SUMMARY = "Behavior change when requesting photo library access"
        private const val ISSUE_EXPLANATION =
            "Selected Photo Access is a new ability for users to share partial access to their " +
                "photo library when apps request access to their device storage on Android 14+ " +
                "(API level 34+). When your app targets Android 14 or higher and requests the " +
                "READ_MEDIA_IMAGES or READ_MEDIA_VIDEO permission, the user is prompted to grant " +
                "selected access to their photos and videos. You should adapt your app to handle " +
                "partial access, for example by using the photo picker (Intent.ACTION_PICK_IMAGES) " +
                "or by handling the READ_MEDIA_VISUAL_USER_SELECTED permission."

        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = ISSUE_SUMMARY,
            explanation = ISSUE_EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != READ_MEDIA_IMAGES && name != READ_MEDIA_VIDEO) {
            return
        }

        context.report(
            ISSUE,
            context.getElementLocation(element),
            "On Android 14+ (API 34+), requesting $name grants selected access to " +
                "photos/videos. Adapt your app to handle partial access."
        )
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No attributes to check.
    }

    override fun checkMergedProject(context: Context) {
        if (context !is XmlContext) return
        val document = context.document ?: return
        val root = document.documentElement ?: return

        var targetSdk: Int? = null
        var offendingElement: Element? = null

        val elements = root.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val node = elements.item(i)
            if (node !is Element) continue

            when (node.tagName) {
                TAG_USES_SDK -> {
                    val targetSdkValue = node.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                    if (targetSdkValue.isNotEmpty()) {
                        targetSdk = targetSdkValue.toIntOrNull()
                    }
                }
                TAG_USES_PERMISSION -> {
                    val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == READ_MEDIA_IMAGES || name == READ_MEDIA_VIDEO) {
                        offendingElement = node
                    }
                }
            }
        }

        if (targetSdk != null && targetSdk >= ANDROID_14_API_LEVEL && offendingElement != null) {
            context.report(
                ISSUE,
                context.getElementLocation(offendingElement),
                "On Android 14+ (API 34+), requesting photo/video permissions grants selected " +
                    "access. Adapt your app to handle partial access."
            )
        }
    }
}