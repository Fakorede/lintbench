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
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class SelectedPhotoAccessDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = "Starting with Android 14, when your app requests READ_MEDIA_IMAGES or READ_MEDIA_VIDEO, the user can choose to grant access to only selected photos and videos. Your app should handle this partial access state gracefully. Alternatively, you can request the READ_MEDIA_VISUAL_USER_SELECTED permission or use the Android Photo Picker.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    }

    override fun checkMergedProject(context: Context) {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newDocumentBuilder().parse(context.file)
        } catch (e: Exception) {
            return
        }

        val root = document.documentElement ?: return
        val permissions = root.getElementsByTagName("uses-permission")

        for (i in 0 until permissions.length) {
            val element = permissions.item(i) as Element
            val name = element.getAttributeNS(ANDROID_URI, "name")
            if (name == READ_MEDIA_IMAGES || name == READ_MEDIA_VIDEO) {
                val location = Location.create(context.file, element)
                context.report(
                    ISSUE,
                    location,
                    "On Android 14+, users can grant partial access to photos and videos when this permission is requested. Ensure your app handles partial access, or consider using the Photo Picker or READ_MEDIA_VISUAL_USER_SELECTED."
                )
            }
        }
    }
}