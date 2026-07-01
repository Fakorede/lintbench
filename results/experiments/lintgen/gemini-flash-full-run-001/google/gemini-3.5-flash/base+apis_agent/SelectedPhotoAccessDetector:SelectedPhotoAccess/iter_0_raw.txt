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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val targetSdk = context.project.targetSdkVersion.featureLevel
        if (targetSdk < 34) {
            return
        }

        val root = document.documentElement ?: return
        val permissions = mutableSetOf<String>()
        val permissionElements = mutableListOf<Element>()

        val usesPermissions = root.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)
        val usesPermissionsSdk23 = root.getElementsByTagName("uses-permission-sdk-23")

        fun collect(list: NodeList) {
            for (i in 0 until list.length) {
                val element = list.item(i) as? Element ?: continue
                val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name.isNotEmpty()) {
                    permissions.add(name)
                    permissionElements.add(element)
                }
            }
        }

        collect(usesPermissions)
        collect(usesPermissionsSdk23)

        val hasReadImages = permissions.contains("android.permission.READ_MEDIA_IMAGES")
        val hasReadVideo = permissions.contains("android.permission.READ_MEDIA_VIDEO")
        val hasVisualUserSelected = permissions.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")

        if ((hasReadImages || hasReadVideo) && !hasVisualUserSelected) {
            val targetElement = permissionElements.firstOrNull {
                val name = it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                name == "android.permission.READ_MEDIA_IMAGES" || name == "android.permission.READ_MEDIA_VIDEO"
            } ?: root

            val location = context.getNameLocation(targetElement)
            context.report(
                ISSUE,
                targetElement,
                location,
                "When targeting Android 14 or higher and requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to support Selected Photo Access"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.
                
                Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                your app to handle partial access to the photo library by declaring the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`.
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