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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.evaluateString

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner {

    companion object {

        private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private val PHOTO_PERMISSIONS = setOf(
            READ_EXTERNAL_STORAGE,
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO
        )

        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to \
                their photo library when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you \
                adapt your app to handle partial access to the photo library.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        ).addMoreInfo("https://developer.android.com/about/versions/14/changes/partial-photo-video-access")

        private val REQUEST_PERMISSION_METHODS = setOf(
            "requestPermissions",
            "requestMultiplePermissions"
        )

        private val ACTIVITY_COMPAT_CLASS = "androidx.core.app.ActivityCompat"
        private val ACTIVITY_CLASS = "android.app.Activity"
        private val FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
        private val FRAGMENT_V4_CLASS = "android.support.v4.app.Fragment"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "requestPermissions",
            "launch",
            "shouldShowRequestPermissionRationale"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            "requestPermissions" -> checkRequestPermissions(context, node, method)
            "launch" -> checkActivityResultLauncher(context, node, method)
        }
    }

    private fun checkRequestPermissions(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isRelevantClass = containingClass == ACTIVITY_COMPAT_CLASS ||
                containingClass == ACTIVITY_CLASS ||
                containingClass == FRAGMENT_CLASS ||
                containingClass == FRAGMENT_V4_CLASS ||
                isSubclassOf(context, containingClass, ACTIVITY_CLASS) ||
                isSubclassOf(context, containingClass, FRAGMENT_CLASS) ||
                isSubclassOf(context, containingClass, FRAGMENT_V4_CLASS)

        if (!isRelevantClass) return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        // Find the permissions array argument
        // For ActivityCompat.requestPermissions(activity, permissions, requestCode)
        // For Activity.requestPermissions(permissions, requestCode)
        val permissionsArg = findPermissionsArgument(containingClass, arguments) ?: return

        if (containsPhotoPermissionWithoutUserSelected(context, permissionsArg)) {
            reportIssue(context, node)
        }
    }

    private fun checkActivityResultLauncher(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        // Check if this is a RequestMultiplePermissions or RequestPermission contract launch
        if (containingClass != "androidx.activity.result.ActivityResultLauncher") return

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]

        if (containsPhotoPermissionWithoutUserSelected(context, firstArg)) {
            reportIssue(context, node)
        }
    }

    private fun findPermissionsArgument(
        containingClass: String,
        arguments: List<UExpression>
    ): UExpression? {
        return when {
            containingClass == ACTIVITY_COMPAT_CLASS -> {
                // ActivityCompat.requestPermissions(activity, permissions[], requestCode)
                if (arguments.size >= 2) arguments[1] else null
            }
            else -> {
                // Activity/Fragment.requestPermissions(permissions[], requestCode)
                if (arguments.isNotEmpty()) arguments[0] else null
            }
        }
    }

    private fun containsPhotoPermissionWithoutUserSelected(
        context: JavaContext,
        permissionsArg: UExpression
    ): Boolean {
        val permissions = extractPermissions(context, permissionsArg)
        if (permissions.isEmpty()) return false

        val hasPhotoPermission = permissions.any { it in PHOTO_PERMISSIONS }
        val hasUserSelectedPermission = permissions.any { it == READ_MEDIA_VISUAL_USER_SELECTED }

        return hasPhotoPermission && !hasUserSelectedPermission
    }

    private fun extractPermissions(
        context: JavaContext,
        expression: UExpression
    ): List<String> {
        val permissions = mutableListOf<String>()
        collectPermissions(context, expression, permissions)
        return permissions
    }

    private fun collectPermissions(
        context: JavaContext,
        expression: UExpression,
        permissions: MutableList<String>
    ) {
        when (expression) {
            is ULiteralExpression -> {
                val value = expression.value as? String
                if (value != null) {
                    permissions.add(value)
                }
            }
            is USimpleNameReferenceExpression -> {
                val evaluated = expression.evaluateString()
                if (evaluated != null) {
                    permissions.add(evaluated)
                } else {
                    // Try to resolve the field reference
                    val resolved = expression.resolve()
                    if (resolved != null) {
                        val resolvedValue = context.evaluator.getAnnotationValue(
                            context.evaluator.getAnnotation(resolved, ""),
                            ""
                        )
                        // Try evaluate via UAST
                        val uastValue = expression.evaluateString()
                        if (uastValue != null) {
                            permissions.add(uastValue)
                        }
                    }
                }
            }
            else -> {
                // Try to evaluate as string
                val evaluated = expression.evaluateString()
                if (evaluated != null) {
                    permissions.add(evaluated)
                } else {
                    // Try to evaluate as array
                    val arrayElements = tryExtractArrayElements(expression)
                    for (element in arrayElements) {
                        collectPermissions(context, element, permissions)
                    }
                }
            }
        }
    }

    private fun tryExtractArrayElements(expression: UExpression): List<UExpression> {
        // Handle arrayOf(...) or new String[]{...} patterns
        if (expression is UCallExpression) {
            val methodName = expression.methodName
            if (methodName == "arrayOf" || methodName == "arrayOfNulls") {
                return expression.valueArguments
            }
            // Handle new String[]{...}
            return expression.valueArguments
        }
        return emptyList()
    }

    private fun isSubclassOf(
        context: JavaContext,
        className: String,
        superClassName: String
    ): Boolean {
        val evaluator = context.evaluator
        val cls = evaluator.findClass(className) ?: return false
        val superClass = evaluator.findClass(superClassName) ?: return false
        return evaluator.extendsClass(cls, superClassName, false) ||
                evaluator.implementsInterface(cls, superClassName, false)
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = ISSUE,
            scope = node as UElement,
            location = context.getCallLocation(node, includeReceiver = false, includeArguments = true),
            message = "On Android 14+, apps requesting photo/video permissions should also " +
                    "request `READ_MEDIA_VISUAL_USER_SELECTED` to support Selected Photo Access. " +
                    "See https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }
}