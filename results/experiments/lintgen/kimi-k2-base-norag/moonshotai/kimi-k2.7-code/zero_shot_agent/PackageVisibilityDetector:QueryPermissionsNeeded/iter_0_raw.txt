package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("getInstalledPackages", "getInstalledApplications")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, parent: UElement?) {
        val targetSdk = context.project.targetSdk
        if (targetSdk != -1 && targetSdk < 30) {
            return
        }

        val receiverType = node.receiverType
        if (receiverType != null &&
            context.evaluator.extendsClass(receiverType, "android.content.pm.PackageManager", false)
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `PackageManager#getInstalledPackages` or `PackageManager#getInstalledApplications` " +
                        "will not return all installed apps when targeting Android 11 (API 30) or higher. " +
                        "Consider adding a `<queries>` declaration to the manifest or using " +
                        "`getPackageInfo`/`queryIntentActivities` instead."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default.
                As a result, `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications`
                will no longer return information about all installed apps. To query specific apps or types
                of apps, add a `<queries>` declaration to the manifest or use methods like
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                See https://g.co/dev/packagevisibility for more details.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}