package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Attr
import com.android.SdkConstants
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class SelectedPhotoAccessDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private const val REQUEST_PERMISSIONS_METHOD = "requestPermissions"
        private const val REQUEST_MULTIPLE_PERMISSIONS_METHOD = "requestMultiplePermissions"
        private const val ACTIVITY_COMPAT = "androidx.core.app.ActivityCompat"
        private const val FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
        private const val ACTIVITY_CLASS = "android.app.Activity"
        private const val REQUEST_PERMISSION_LAUNCHER = "requestPermissionLauncher"

        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access \
                to their photo library when apps request access to their device storage \
                on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library.

                To handle this correctly, when requesting `READ_MEDIA_IMAGES` or \
                `READ_MEDIA_VIDEO` permissions, you should also request the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission to support the partial \
                photo/video access flow introduced in Android 14.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.JAVA_FILE)
            ),
            moreInfo = "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )

        private fun hasMediaPermission(permissions: Set<String>): Boolean {
            return permissions.contains(READ_MEDIA_IMAGES) || permissions.contains(READ_MEDIA_VIDEO)
        }

        private fun hasUserSelectedPermission(permissions: Set<String>): Boolean {
            return permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)
        }
    }

    // -------------------------------------------------------------------------
    // XML / Manifest scanning
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr: Attr =
            element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                ?: return
        val permissionName = nameAttr.value ?: return

        // We only care about READ_MEDIA_IMAGES / READ_MEDIA_VIDEO declarations
        if (permissionName != READ_MEDIA_IMAGES && permissionName != READ_MEDIA_VIDEO) {
            return
        }

        // Check whether READ_MEDIA_VISUAL_USER_SELECTED is also declared in the manifest
        val document = element.ownerDocument ?: return
        val allUsesPermissions = document.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION)
        val declaredPermissions = mutableSetOf<String>()
        for (i in 0 until allUsesPermissions.length) {
            val node = allUsesPermissions.item(i) as? Element ?: continue
            val name = node.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (!name.isNullOrEmpty()) {
                declaredPermissions.add(name)
            }
        }

        if (!hasUserSelectedPermission(declaredPermissions)) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "When requesting `$permissionName`, you should also declare " +
                        "`READ_MEDIA_VISUAL_USER_SELECTED` to support partial photo/video " +
                        "access on Android 14+. See " +
                        "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Java / Kotlin source scanning
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            REQUEST_PERMISSIONS_METHOD,
            "requestPermissions",
            "launch"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            "requestPermissions" -> checkRequestPermissionsCall(context, node, method)
            "launch" -> checkActivityResultLauncherCall(context, node, method)
        }
    }

    private fun checkRequestPermissionsCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator

        // Match ActivityCompat.requestPermissions(activity, permissions, requestCode)
        // or Activity.requestPermissions(permissions, requestCode)
        // or Fragment.requestPermissions(permissions, requestCode)
        val isActivityCompat = evaluator.isMemberInClass(method, ACTIVITY_COMPAT)
        val isActivity = evaluator.extendsClass(
            evaluator.getTypeClass(method.containingClass?.let {
                context.evaluator.getClassType(it)
            }),
            ACTIVITY_CLASS,
            true
        )
        val isFragment = evaluator.isMemberInClass(method, FRAGMENT_CLASS) ||
                (method.containingClass?.qualifiedName?.contains("Fragment") == true)

        if (!isActivityCompat && !isActivity && !isFragment) {
            // Try a broader check – any class that has a requestPermissions method
            // with a String array parameter
            val containingClass = method.containingClass ?: return
            val qualifiedName = containingClass.qualifiedName ?: ""
            val hasPermissionsParam = method.parameterList.parameters.any { param ->
                param.type.canonicalText.contains("String")
            }
            if (!hasPermissionsParam) return
        }

        // Find the permissions array argument
        val permissionsArg = findPermissionsArgument(node) ?: return
        val permissions = extractStringValues(context, permissionsArg) ?: return

        if (hasMediaPermission(permissions) && !hasUserSelectedPermission(permissions)) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "When requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, " +
                        "you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to " +
                        "support partial photo/video access on Android 14+. See " +
                        "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    private fun checkActivityResultLauncherCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Check if this is an ActivityResultLauncher<Array<String>>.launch() call
        // used with RequestMultiplePermissions contract
        val containingClass = method.containingClass ?: return
        val className = containingClass.qualifiedName ?: ""

        if (!className.contains("ActivityResultLauncher") &&
            !className.contains("ManagedActivityResultLauncher")
        ) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return

        val permissionsArg = args[0]
        val permissions = extractStringValues(context, permissionsArg) ?: return

        if (hasMediaPermission(permissions) && !hasUserSelectedPermission(permissions)) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "When requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, " +
                        "you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to " +
                        "support partial photo/video access on Android 14+. See " +
                        "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
            )
        }
    }

    /**
     * Attempt to find the permissions array argument from a requestPermissions call.
     *
     * Handles:
     *  - ActivityCompat.requestPermissions(activity, permissionsArray, requestCode) -> arg index 1
     *  - activity.requestPermissions(permissionsArray, requestCode) -> arg index 0
     *  - fragment.requestPermissions(permissionsArray, requestCode) -> arg index 0
     */
    private fun findPermissionsArgument(
        node: UCallExpression
    ): org.jetbrains.uast.UExpression? {
        val args = node.valueArguments
        if (args.isEmpty()) return null

        // Heuristic: if first arg type looks like Activity/Context, permissions is at index 1
        // otherwise at index 0
        return when {
            args.size >= 3 -> args[1] // ActivityCompat style
            args.size >= 2 -> args[0] // Activity/Fragment style
            else -> null
        }
    }

    /**
     * Try to extract a set of string literals from a UAST expression.
     * Handles array literals and arrayOf() / arrayOfNulls() calls.
     */
    private fun extractStringValues(
        context: JavaContext,
        expression: org.jetbrains.uast.UExpression
    ): Set<String>? {
        val result = mutableSetOf<String>()

        when (expression) {
            is org.jetbrains.uast.UCallExpression -> {
                // arrayOf(...), arrayOfNulls(...), etc.
                for (arg in expression.valueArguments) {
                    val value = ConstantEvaluator.evaluate(context, arg)
                    if (value is String) {
                        result.add(value)
                    }
                }
                if (result.isNotEmpty()) return result
            }
            is org.jetbrains.uast.ULiteralExpression -> {
                val value = expression.value
                if (value is String) {
                    result.add(value)
                    return result
                }
            }
            else -> {
                // Try constant evaluation (handles references to string arrays defined elsewhere)
                val evaluated = ConstantEvaluator.evaluate(context, expression)
                when (evaluated) {
                    is String -> result.add(evaluated)
                    is Array<*> -> evaluated.filterIsInstance<String>().forEach { result.add(it) }
                    else -> {
                        // Try evaluating as an array
                        val arr = ConstantEvaluator.evaluateString(context, expression, false)
                        if (arr != null) result.add(arr)
                    }
                }
                if (result.isNotEmpty()) return result
            }
        }

        return if (result.isNotEmpty()) result else null
    }
}