package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SelectedPhotoAccessDetector::class.java,
            Scope.JAVA_AND_RESOURCE_FILES,
            Scope.JAVA_FILE_SCOPE,
            Scope.RESOURCE_FILE_SCOPE
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

                See https://developer.android.com/about/versions/14/changes/partial-photo-video-access \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            androidSpecific = true
        ).addMoreInfo("https://developer.android.com/about/versions/14/changes/partial-photo-video-access")

        // Permissions that trigger the selected photo access behavior change
        private val PHOTO_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )

        // The new granular permission introduced in Android 14
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private const val ACTIVITY_COMPAT = "androidx.core.app.ActivityCompat"
        private const val ACTIVITY = "android.app.Activity"
        private const val FRAGMENT = "androidx.fragment.app.Fragment"

        private const val REQUEST_PERMISSIONS = "requestPermissions"
        private const val SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE =
            "shouldShowRequestPermissionRationale"
    }

    // -------------------------------------------------------------------------
    // XmlScanner – check AndroidManifest.xml for photo-related permissions
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android", "name"
        ) ?: return

        if (name in PHOTO_PERMISSIONS) {
            // Check whether READ_MEDIA_VISUAL_USER_SELECTED is also declared
            val manifest = element.ownerDocument ?: return
            val permissions = manifest.getElementsByTagName("uses-permission")
            var hasUserSelected = false
            for (i in 0 until permissions.length) {
                val perm = permissions.item(i) as? org.w3c.dom.Element ?: continue
                val permName = perm.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name"
                )
                if (permName == READ_MEDIA_VISUAL_USER_SELECTED) {
                    hasUserSelected = true
                    break
                }
            }

            if (!hasUserSelected) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "When targeting Android 14+, consider also declaring " +
                            "`$READ_MEDIA_VISUAL_USER_SELECTED` to handle partial " +
                            "photo library access gracefully"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner – check runtime permission request calls
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf(REQUEST_PERMISSIONS, SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator

        val isRelevantMethod = method.name == REQUEST_PERMISSIONS ||
                method.name == SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE

        if (!isRelevantMethod) return

        val isInRelevantClass = evaluator.isMemberInClass(method, ACTIVITY_COMPAT) ||
                evaluator.isMemberInClass(method, ACTIVITY) ||
                evaluator.isMemberInClass(method, FRAGMENT) ||
                run {
                    val containingClass = method.containingClass
                    containingClass != null && (
                            evaluator.extendsClass(containingClass, ACTIVITY, false) ||
                                    evaluator.extendsClass(containingClass, FRAGMENT, false)
                            )
                }

        if (!isInRelevantClass) return

        // Look for photo-related permissions in the arguments
        val args = node.valueArguments
        var foundPhotoPermission = false
        var foundUserSelected = false

        for (arg in args) {
            val value = ConstantEvaluator.evaluate(context, arg)
            if (value is String) {
                if (value in PHOTO_PERMISSIONS) foundPhotoPermission = true
                if (value == READ_MEDIA_VISUAL_USER_SELECTED) foundUserSelected = true
            } else if (value is Array<*>) {
                for (item in value) {
                    if (item is String) {
                        if (item in PHOTO_PERMISSIONS) foundPhotoPermission = true
                        if (item == READ_MEDIA_VISUAL_USER_SELECTED) foundUserSelected = true
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