package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
            Scope.MANIFEST_SCOPE,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you \
                adapt your app to handle partial access to the photo library.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            androidSpecific = true
        ).addMoreInfo("https://developer.android.com/about/versions/14/changes/partial-photo-video-access")

        private val PHOTO_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )

        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private const val USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"

        private const val REQUEST_PERMISSIONS = "requestPermissions"
        private const val CHECK_SELF_PERMISSION = "checkSelfPermission"
        private const val SHOULD_SHOW_RATIONALE = "shouldShowRequestPermissionRationale"
    }

    // -------------------------------------------------------------------------
    // XmlScanner – check AndroidManifest.xml for photo-related permissions
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME) ?: return

        if (name !in PHOTO_PERMISSIONS) return

        // Check whether READ_MEDIA_VISUAL_USER_SELECTED is also declared
        val manifest = element.ownerDocument ?: return
        val permissions = manifest.getElementsByTagName(USES_PERMISSION)
        for (i in 0 until permissions.length) {
            val perm = permissions.item(i) as? Element ?: continue
            val permName = perm.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
            if (permName == READ_MEDIA_VISUAL_USER_SELECTED) {
                return // Already handles partial access
            }
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "When targeting Android 14+, consider also declaring " +
                    "`$READ_MEDIA_VISUAL_USER_SELECTED` to handle partial " +
                    "photo library access gracefully"
        )
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner – check runtime permission request calls
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        REQUEST_PERMISSIONS,
        CHECK_SELF_PERMISSION,
        SHOULD_SHOW_RATIONALE
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        var foundPhotoPermission = false
        var foundUserSelected = false

        for (arg in node.valueArguments) {
            val value = ConstantEvaluator.evaluate(context, arg)
            when (value) {
                is String -> {
                    if (value in PHOTO_PERMISSIONS) foundPhotoPermission = true
                    if (value == READ_MEDIA_VISUAL_USER_SELECTED) foundUserSelected = true
                }
                is Array<*> -> {
                    for (item in value) {
                        if (item is String) {
                            if (item in PHOTO_PERMISSIONS) foundPhotoPermission = true
                            if (item == READ_MEDIA_VISUAL_USER_SELECTED) foundUserSelected = true
                        }
                    }
                }
            }
        }

        if (foundPhotoPermission && !foundUserSelected) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "When requesting photo/video permissions on Android 14+, consider also " +
                        "requesting `$READ_MEDIA_VISUAL_USER_SELECTED` to support " +
                        "partial photo library access"
            )
        }
    }
}