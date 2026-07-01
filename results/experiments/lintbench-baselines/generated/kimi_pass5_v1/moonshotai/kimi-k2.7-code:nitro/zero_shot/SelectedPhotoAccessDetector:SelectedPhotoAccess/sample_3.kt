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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UastCallKind

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> =
        listOf("requestPermissions", "requestPermission", "launch")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        when (node.methodName) {
            "requestPermissions", "requestPermission" -> {
                checkArguments(context, node)
            }
            "launch" -> {
                val containingClass = method.containingClass?.qualifiedName
                if (containingClass == "androidx.activity.result.ActivityResultLauncher") {
                    checkArguments(context, node)
                }
            }
        }
    }

    private fun checkArguments(context: JavaContext, node: UCallExpression) {
        if (node.valueArguments.any { it.containsTargetPermission() }) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "On Android 14+, requesting media storage access can trigger selected " +
                    "photo/video access. Consider using the Android Photo Picker or " +
                    "adapting your app to handle partial access."
            )
        }
    }

    private fun UExpression.containsTargetPermission(): Boolean {
        return when (this) {
            is ULiteralExpression -> {
                ConstantEvaluator.evaluateString(this) in TARGET_PERMISSIONS
            }
            is UCallExpression -> {
                when {
                    methodName == "arrayOf" ||
                        methodName == "arrayOfNotNull" ||
                        methodName == "Array" ||
                        kind == UastCallKind.NEW_ARRAY -> {
                        valueArguments.any { it.containsTargetPermission() }
                    }
                    else -> ConstantEvaluator.evaluateString(this) in TARGET_PERMISSIONS
                }
            }
            is UPolyadicExpression -> operands.any { it.containsTargetPermission() }
            else -> {
                when (val value = ConstantEvaluator.evaluate(this)) {
                    is String -> value in TARGET_PERMISSIONS
                    is Array<*> -> value.filterIsInstance<String>().any { it in TARGET_PERMISSIONS }
                    is List<*> -> value.filterIsInstance<String>().any { it in TARGET_PERMISSIONS }
                    else -> ConstantEvaluator.evaluateString(this) in TARGET_PERMISSIONS
                }
            }
        }
    }

    companion object {
        private val TARGET_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Starting in Android 14, when an app requests `READ_MEDIA_IMAGES` or
                `READ_MEDIA_VIDEO` the user can choose to grant access only to selected
                photos and videos. The system then manages that partial-access selection.

                Instead of relying on the system dialog, consider using the Android Photo
                Picker (for example `ActivityResultContracts.PickVisualMedia`), which
                does not require storage permissions and provides a consistent
                experience. Alternatively, adapt your app to handle partial access by
                checking for and querying the user's current selection.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}