package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import java.util.EnumSet

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("requestPermissions", "launch")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        if (context.mainProject.targetSdk < ANDROID_14_TARGET_SDK) return

        when (node.methodName) {
            "requestPermissions" -> {
                node.valueArguments.forEach { checkPermissionArgument(context, it) }
            }
            "launch" -> {
                val containingClass = node.resolve()?.containingClass?.qualifiedName ?: ""
                if (containingClass.startsWith("androidx.activity.result.ActivityResultLauncher")) {
                    node.valueArguments.forEach { checkPermissionArgument(context, it) }
                }
            }
        }
    }

    private fun checkPermissionArgument(context: JavaContext, argument: UExpression) {
        for (permission in evaluatePermissionStrings(context, argument)) {
            if (permission in AFFECTED_PERMISSIONS) {
                val message = "On Android 14+, requesting `$permission` may grant only partial access " +
                        "to the photo library. Consider using the Photo Picker or adapting your app " +
                        "to handle partial access."
                context.report(ISSUE, argument, context.getLocation(argument), message)
            }
        }
    }

    private fun evaluatePermissionStrings(context: JavaContext, expression: UExpression): Set<String> {
        val result = mutableSetOf<String>()

        when (val value = ConstantEvaluator.evaluate(context, expression)) {
            is String -> result.add(value)
            is Array<*> -> value.filterIsInstance<String>().forEach { result.add(it) }
            is List<*> -> value.filterIsInstance<String>().forEach { result.add(it) }
        }

        if (expression is UCallExpression) {
            val name = expression.methodName
            if (name == "arrayOf" || name == "listOf" || name == null) {
                expression.valueArguments.forEach { result.addAll(evaluatePermissionStrings(context, it)) }
            }
        }

        return result
    }

    companion object {
        private const val ANDROID_14_TARGET_SDK = 34

        private val AFFECTED_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )

        @JvmField
        val ISSUE = Issue.create(
            "SelectedPhotoAccess",
            "Behavior change when requesting photo library access",
            """
                Starting with Android 14, when an app requests read access to the user's photo library
                (READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, or READ_EXTERNAL_STORAGE), the user can choose
                to grant only partial access. Instead of letting the system manage the selection lifecycle,
                adapt your app to handle partial access, for example by using the Photo Picker.
                """.trimIndent(),
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            ),
            "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
    }
}