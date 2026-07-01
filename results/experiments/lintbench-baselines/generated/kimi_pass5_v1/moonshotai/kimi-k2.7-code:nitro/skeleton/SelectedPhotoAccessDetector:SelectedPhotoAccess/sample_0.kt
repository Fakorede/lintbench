package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        private const val ANDROID_14_API_LEVEL = 34

        private val PHOTO_ACCESS_PERMISSIONS = listOf(
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                On Android 14+ (API 34+), users can grant an app only partial access to the
                photo library when it requests storage-related permissions such as
                READ_MEDIA_IMAGES or READ_MEDIA_VIDEO.

                Instead of letting the system manage the selection lifecycle, your app
                should be adapted to handle partial access. Consider also declaring
                READ_MEDIA_VISUAL_USER_SELECTED so you can detect limited access, and
                provide a way for users to expand their selection by requesting the
                relevant permission again. Alternatively, use the Photo Picker for a
                privacy-friendly way to let users select specific media.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        val manifestFile = project.manifestFile ?: return
        if (!manifestFile.isFile) return

        val manifestText = manifestFile.readText()

        val targetSdk = if (project.targetSdk > 0) {
            project.targetSdk
        } else {
            manifestText.extractTargetSdk() ?: 0
        }

        if (targetSdk < ANDROID_14_API_LEVEL) return

        val declaredPermissions = (PHOTO_ACCESS_PERMISSIONS + READ_MEDIA_VISUAL_USER_SELECTED)
            .filter { manifestText.containsPermission(it) }
            .toSet()

        val requestsPhotoAccess = PHOTO_ACCESS_PERMISSIONS.any { it in declaredPermissions }
        if (requestsPhotoAccess && READ_MEDIA_VISUAL_USER_SELECTED !in declaredPermissions) {
            val location = Location.create(manifestFile)
            context.report(
                ISSUE,
                location,
                "App requests photo library access but does not declare "
                    + "READ_MEDIA_VISUAL_USER_SELECTED; adapt it to handle partial photo "
                    + "access on Android 14+.",
            )
        }
    }

    private fun String.containsPermission(permission: String): Boolean {
        return contains(
            Regex(
                "<uses-permission[^>]*android:name\\s*=\\s*[\"'][^\"']*$permission[\"']"
            )
        )
    }

    private fun String.extractTargetSdk(): Int? {
        val match = Regex("android:targetSdkVersion\\s*=\\s*[\"'](\\d+)[\"']").find(this)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}