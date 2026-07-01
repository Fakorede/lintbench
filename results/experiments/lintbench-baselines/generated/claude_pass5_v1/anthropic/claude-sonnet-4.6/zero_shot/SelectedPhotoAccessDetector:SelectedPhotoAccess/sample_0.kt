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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

@Suppress("UnstableApiUsage")
class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_MEDIA_VISUAL_USER_SELECTED =
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private val PHOTO_VIDEO_PERMISSIONS = setOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)

        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access \
                to their photo library when apps request access to their device storage \
                on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend \
                you adapt your app to handle partial access to the photo library.

                To handle this properly, you should also request the \
                `READ_MEDIA_VISUAL_USER_SELECTED` permission alongside `READ_MEDIA_IMAGES` \
                or `READ_MEDIA_VIDEO`. This allows your app to properly handle the case \
                where the user grants only partial access to their photo library.

                Reference: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        ).addMoreInfo(
            "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("requestPermissions", "requestMultiplePermissions", "launch")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when {
            methodName == "requestPermissions" -> {
                checkRequestPermissions(context, node)
            }
            methodName == "launch" -> {
                checkActivityResultLaunch(context, node)
            }
            methodName == "requestMultiplePermissions" -> {
                checkRequestMultiplePermissions(context, node)
            }
        }
    }

    private fun checkRequestPermissions(context: JavaContext, node: UCallExpression) {
        val args = node.valueArguments
        if (args.size < 2) return

        // requestPermissions(Activity, String[], int) or
        // ActivityCompat.requestPermissions(Activity, String[], int)
        // Find the permissions array argument
        val permissionsArg = findPermissionsArrayArg(args) ?: return

        val permissions = extractPermissionsFromArg(context, permissionsArg)
        if (permissions.isEmpty()) return

        if (containsPhotoVideoPermission(permissions) &&
            !permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            reportIssue(context, node)
        }
    }

    private fun checkActivityResultLaunch(context: JavaContext, node: UCallExpression) {
        // Check if this is a RequestMultiplePermissions contract launch
        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType()?.canonicalText ?: return

        // ActivityResultLauncher<Array<String>> or similar
        if (!receiverType.contains("ActivityResultLauncher")) return

        val args = node.valueArguments
        if (args.isEmpty()) return

        val permissionsArg = args[0]
        val permissions = extractPermissionsFromArg(context, permissionsArg)
        if (permissions.isEmpty()) return

        if (containsPhotoVideoPermission(permissions) &&
            !permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            reportIssue(context, node)
        }
    }

    private fun checkRequestMultiplePermissions(context: JavaContext, node: UCallExpression) {
        // This handles the Kotlin coroutines or other APIs named requestMultiplePermissions
        val args = node.valueArguments
        if (args.isEmpty()) return

        val permissionsArg = args[0]
        val permissions = extractPermissionsFromArg(context, permissionsArg)
        if (permissions.isEmpty()) return

        if (containsPhotoVideoPermission(permissions) &&
            !permissions.contains(READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            reportIssue(context, node)
        }
    }

    private fun findPermissionsArrayArg(args: List<UExpression>): UExpression? {
        // In requestPermissions(Activity, String[], int), the array is at index 1
        // In ActivityCompat.requestPermissions(Activity, String[], int), same
        // Try to find an array argument
        for (arg in args) {
            val type = arg.getExpressionType()
            if (type != null && (type.canonicalText == "java.lang.String[]" ||
                        type.canonicalText == "kotlin.Array<java.lang.String>" ||
                        type.canonicalText.contains("String[]") ||
                        type.canonicalText.contains("Array"))
            ) {
                return arg
            }
        }
        // Fallback: return second argument if it exists (common pattern)
        return if (args.size >= 2) args[1] else if (args.isNotEmpty()) args[0] else null
    }

    private fun extractPermissionsFromArg(
        context: JavaContext,
        arg: UExpression
    ): Set<String> {
        val permissions = mutableSetOf<String>()
        arg.accept(object : AbstractUastVisitor() {
            override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
                val value = node.value
                if (value is String) {
                    permissions.add(value)
                }
                return super.visitLiteralExpression(node)
            }

            override fun visitSimpleNameReferenceExpression(
                node: USimpleNameReferenceExpression
            ): Boolean {
                val resolved = node.resolve()
                if (resolved != null) {
                    val evaluator = context.evaluator
                    val evaluated = context.evaluator.let {
                        try {
                            ConstantEvaluator.evaluate(context, node)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (evaluated is String) {
                        permissions.add(evaluated)
                    }
                }
                return super.visitSimpleNameReferenceExpression(node)
            }
        })

        // Also try direct constant evaluation on the whole expression
        try {
            val evaluated = ConstantEvaluator.evaluate(context, arg)
            if (evaluated is String) {
                permissions.add(evaluated)
            } else if (evaluated is Array<*>) {
                for (item in evaluated) {
                    if (item is String) permissions.add(item)
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return permissions
    }

    private fun containsPhotoVideoPermission(permissions: Set<String>): Boolean {
        return permissions.any { it in PHOTO_VIDEO_PERMISSIONS }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = false, includeArguments = true),
            message = "When requesting `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`, " +
                    "you should also request `READ_MEDIA_VISUAL_USER_SELECTED` to handle " +
                    "partial photo library access on Android 14+."
        )
    }
}