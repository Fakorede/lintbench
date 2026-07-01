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
import org.jetbrains.uast.UastCallKind

class SelectedPhotoAccessDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> {
        return listOf(
            "requestPermissions",
            "checkSelfPermission",
            "checkCallingPermission",
            "checkCallingOrSelfPermission",
            "enforcePermission",
            "enforceCallingPermission",
            "enforceCallingOrSelfPermission",
            "launch"
        )
    }

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        method: com.intellij.psi.PsiMethod?
    ) {
        val targetSdk = context.mainProject.targetSdkVersion
        if (!targetSdk.isAtLeast(34)) {
            return
        }

        for (arg in call.valueArguments) {
            checkArgument(context, call, arg)
        }
    }

    private fun checkArgument(
        context: JavaContext,
        call: UCallExpression,
        arg: UExpression
    ) {
        val value = ConstantEvaluator.evaluate(context, arg)
        when (value) {
            is String -> if (value in PHOTO_PERMISSIONS) report(context, call, value)
            is Array<*> -> value.forEach {
                if (it is String && it in PHOTO_PERMISSIONS) report(context, call, it)
            }
            is List<*> -> value.forEach {
                if (it is String && it in PHOTO_PERMISSIONS) report(context, call, it)
            }
        }

        if (arg is UCallExpression) {
            val name = arg.methodIdentifier?.name
            val kind = arg.kind
            if (name in COLLECTION_INITIALIZER_NAMES || kind == UastCallKind.NEW_ARRAY) {
                for (initializerArg in arg.valueArguments) {
                    checkArgument(context, call, initializerArg)
                }
            }
        }
    }

    private fun report(context: JavaContext, call: UCallExpression, permission: String) {
        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "Requesting `$permission` may result in partial (selected) photo access on " +
                "Android 14+; ensure your app handles partial access correctly, such as by " +
                "using the photo picker or gracefully degrading when only some items are " +
                "available."
        )
    }

    companion object {
        private val PHOTO_PERMISSIONS = setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )

        private val COLLECTION_INITIALIZER_NAMES = setOf(
            "arrayOf",
            "arrayOfNotNull",
            "listOf",
            "mutableListOf",
            "listOfNotNull"
        )

        private const val ID = "SelectedPhotoAccess"

        val ISSUE = Issue.create(
            ID,
            "Selected photo access behavior change on Android 14+",
            "On Android 14+ (API 34+), when an app requests access to the user's photo " +
                "library via READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, or READ_EXTERNAL_STORAGE, " +
                "the user can choose to grant access only to selected photos and videos. " +
                "Apps should be updated to handle this partial access rather than assuming " +
                "full library access. Consider using the photo picker or the Storage Access " +
                "Framework, and ensure that any features relying on broad media access " +
                "degrade gracefully. For more details, see " +
                "https://developer.android.com/about/versions/14/changes/partial-photo-video-access.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(
                SelectedPhotoAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}