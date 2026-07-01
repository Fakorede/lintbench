package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps \
                by default. If you need to query or interact with other installed apps, you may need \
                to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
                """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            val targetSdk = context.project.targetSdkVersion.featureLevel
            if (targetSdk >= 30 || targetSdk < 0) {
                val message = "Using `${method.name}` when targeting Android 11 or higher " +
                        "will return a filtered list of apps. Consider using specific query methods " +
                        "or adding a `<queries>` declaration to your manifest."
                context.report(ISSUE, node, context.getLocation(node), message)
            }
        }
    }
}