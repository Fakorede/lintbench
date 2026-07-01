package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.evaluateString

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner {

    companion object {

        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

        private val PHOTO_PERMISSIONS = setOf(
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO,
            READ_EXTERNAL_STORAGE
        )

        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access \
                to their photo library when apps request access to their device storage \
                on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "requestPermissions",
            "requestMultiplePermissions",
            "shouldShowRequestPermissionRationale",
            "checkSelfPermission",
            "checkPermission"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            "requestPermissions" -> checkRequestPermissions(context, node)
            "requestMultiplePermissions" -> checkRequestMultiplePermissions(context, node)
            "shouldShowRequestPermissionRationale" -> checkSinglePermissionArg(context, node)
            "checkSelfPermission" -> checkSinglePermissionArg(context, node)
            "checkPermission" -> checkSinglePermissionArg(context, node)
        }
    }

    private fun checkRequestPermissions(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        // ActivityCompat.requestPermissions(activity, permissions, requestCode)
        // Activity.requestPermissions(permissions, requestCode)
        // Find the String[] argument
        val permissionsArg = findPermissionsArrayArg(arguments) ?: return
        val permissions = extractPermissions(permissionsArg)
        if (permissions != null) {
            checkPermissionsSet(context, node, permissions)
        }
    }

    private fun checkRequestMultiplePermissions(context: JavaContext, node: UCallExpression) {
        // Used with ActivityResultContracts.RequestMultiplePermissions()
        // The permissions are typically passed when launching, not at construction time.
        // We check the arguments of the call for any permission strings.
        val arguments = node.valueArguments
        for (arg in arguments) {
            val permissions = extractPermissions(arg)
            if (permissions != null) {
                checkPermissionsSet(context, node, permissions)
                return
            }
        }
    }

    private fun checkSinglePermissionArg(context: JavaContext, node: UCallExpression) {
        val arguments = node.valueArguments
        for (arg in arguments) {
            val value = arg.evaluateString() ?: continue
            if (value in PHOTO_PERMISSIONS) {
                if (!isUserSelectedAlsoRequested(node)) {
                    reportIssue(context, node)
                }
                return
            }
        }
    }

    private fun checkPermissionsSet(
        context: JavaContext,
        node: UCallExpression,
        permissions: Set<String>
    ) {
        val hasPhotoPermission = permissions.any { it in PHOTO_PERMISSIONS }
        if (hasPhotoPermission) {
            val hasUserSelected = READ_MEDIA_VISUAL_USER_SELECTED in permissions
            if (!hasUserSelected) {
                reportIssue(context, node)
            }
        }
    }

    private fun findPermissionsArrayArg(arguments: List<UExpression>): UExpression? {
        for (arg in arguments) {
            if (arg.isArrayInitializer()) {
                return arg
            }
            // Could be a reference or a new array expression
            val evaluated = arg.evaluate()
            if (evaluated is Array<*>) {
                return arg
            }
            // Check if it's a String array type by evaluating
            val type = arg.getExpressionType()
            if (type != null && (type.canonicalText == "java.lang.String[]" || type.canonicalText == "String[]")) {
                return arg
            }
        }
        // Fallback: return the first non-primitive argument if any
        return arguments.firstOrNull { arg ->
            val type = arg.getExpressionType()
            type != null && type.canonicalText.contains("String")
        }
    }

    private fun extractPermissions(arg: UExpression): Set<String>? {
        val permissions = mutableSetOf<String>()

        // Try direct string evaluation
        val directValue = arg.evaluateString()
        if (directValue != null) {
            permissions.add(directValue)
            return permissions
        }

        // Try array initializer
        if (arg.isArrayInitializer()) {
            val callExpr = arg as? UCallExpression ?: return null
            for (element in callExpr.valueArguments) {
                val value = element.evaluateString() ?: continue
                permissions.add(value)
            }
            return if (permissions.isNotEmpty()) permissions else null
        }

        // Try evaluating as an object array
        val evaluated = arg.evaluate()
        if (evaluated is Array<*>) {
            for (element in evaluated) {
                if (element is String) {
                    permissions.add(element)
                }
            }
            return if (permissions.isNotEmpty()) permissions else null
        }

        return null
    }

    private fun isUserSelectedAlsoRequested(node: UCallExpression): Boolean {
        // Check if within the same call the READ_MEDIA_VISUAL_USER_SELECTED is present
        // This is mainly for single-permission check methods - they can't include it
        // So we always return false for single-permission methods
        return false
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "When requesting photo/video permissions, consider also requesting " +
                "`READ_MEDIA_VISUAL_USER_SELECTED` to handle partial photo library access " +
                "on Android 14+. See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }
}