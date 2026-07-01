package com.android.tools.lint.checks

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.*

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_14 = 34
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"

        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private val MEDIA_PERMISSIONS = setOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)

        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                On Android 14+ (API 34+), when an app requests `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`,
                the user can choose to grant access only to selected photos and videos.

                To handle this partial access correctly, declare and also request the
                `READ_MEDIA_VISUAL_USER_SELECTED` permission. This lets your app detect when
                partial access is granted and behave accordingly.

                If your app uses the Storage Access Framework (SAF) or a photo picker, no changes
                are required.
            """,
            moreInfo = "https://developer.android.com/about/versions/14/changes/partial-photo-video-access",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return emptyList()
    }

    override fun checkMergedProject(context: Context) {
        val project = context.project ?: return
        val targetSdk = project.targetSdkVersion?.featureLevel ?: return
        if (targetSdk < ANDROID_14) {
            return
        }

        val client = context.client
        val manifest = client.getMergedManifest(project) ?: return
        val document = client.xmlParser.parseXml(manifest) ?: return

        val mediaPermissions = mutableListOf<String>()
        var hasUserSelected = false

        val nodes = document.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotBlank() }
                ?: continue

            when {
                name == READ_MEDIA_VISUAL_USER_SELECTED -> hasUserSelected = true
                name in MEDIA_PERMISSIONS -> mediaPermissions.add(name)
            }
        }

        if (mediaPermissions.isNotEmpty() && !hasUserSelected) {
            context.report(
                ISSUE,
                Location.create(manifest),
                "On Android 14+, also request `READ_MEDIA_VISUAL_USER_SELECTED` to support selected photo access."
            )
        }
    }
}