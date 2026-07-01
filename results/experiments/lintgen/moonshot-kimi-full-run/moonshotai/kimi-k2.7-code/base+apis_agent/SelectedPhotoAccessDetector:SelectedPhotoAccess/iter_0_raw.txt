package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString
import org.w3c.dom.Element

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "requestPermissions") return

                for (arg in node.valueArguments) {
                    if (arg.referencesPhotoPermission()) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            MESSAGE
                        )
                        return
                    }
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name in RELEVANT_PERMISSIONS) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "On Android 14+, declaring $name may result in users granting selected photo access only. $MESSAGE"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their \
                photo library when apps request access to their device storage on Android 14+.
                
                Instead of letting the system manage the selection lifecycle, adapt your app to \
                handle partial access to the photo library. Consider using a photo picker such as \
                `ActivityResultContracts.PickVisualMedia` for one-time or persistent access to \
                selected items, and register to handle `MediaStore.ACTION_REQUEST_MEDIA_PERMISSION` \
                if you need to update the user's selection.
            """,
            moreInfo = "https://developer.android.com/about/versions/14/changes/partial-photo-video-access",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.MANIFEST,
                Scope.JAVA_FILE
            )
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val MESSAGE = "Adapt your app to handle partial access to the photo library on Android 14+."
        private val RELEVANT_PERMISSIONS = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO"
        )

        private fun UExpression.referencesPhotoPermission(): Boolean {
            evaluateString()?.let { return it in RELEVANT_PERMISSIONS }
            if (this is UCallExpression) {
                return valueArguments.any { it.referencesPhotoPermission() }
            }
            return false
        }
    }
}