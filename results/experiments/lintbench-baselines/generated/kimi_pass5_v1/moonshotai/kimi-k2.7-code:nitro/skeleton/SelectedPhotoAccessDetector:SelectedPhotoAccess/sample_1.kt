package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        val targetSdk = project.targetSdkVersion.apiLevel
        if (targetSdk < ANDROID_14) {
            return
        }

        val manifest = project.mergedManifest ?: return
        val document = parseManifest(manifest)
        val permissions = collectPermissions(document)

        val requestsPhotoAccess = permissions.any { it in PHOTO_ACCESS_PERMISSIONS }
        val handlesPartialAccess = USER_SELECTED_PERMISSION in permissions

        if (requestsPhotoAccess && !handlesPartialAccess) {
            val location = Location.create(manifest)
            val message = "This app requests media storage access. On Android 14+ users can " +
                "grant only selected photos and videos. Declare " +
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED in the manifest " +
                "and adapt the app to handle partial access."
            context.report(Incident(ISSUE, location, message))
        }
    }

    private fun parseManifest(manifest: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newDocumentBuilder().parse(manifest)
    }

    private fun collectPermissions(document: Document): Set<String> {
        val permissions = mutableSetOf<String>()
        val nodes = document.getElementsByTagName("uses-permission")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val name = element.getAttribute("android:name")
                .takeIf { it.isNotBlank() }
                ?: element.getAttribute("name")
            if (name.isNotBlank()) {
                permissions.add(name)
            }
        }
        return permissions
    }

    companion object {
        private const val ANDROID_14 = 34
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        private const val USER_SELECTED_PERMISSION =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private val PHOTO_ACCESS_PERMISSIONS = setOf(
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO,
            READ_EXTERNAL_STORAGE,
        )

        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                On Android 14 (API 34+) the permission dialog for media access lets users
                grant partial access to their photo library. If your app requests
                `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or `READ_EXTERNAL_STORAGE`,
                it must be prepared for users choosing only selected items.

                To support selected photo access, declare
                `android.permission.READ_MEDIA_VISUAL_USER_SELECTED` in your manifest
                and adapt your permission logic to handle partial access gracefully.
                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access
                for details.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }
}