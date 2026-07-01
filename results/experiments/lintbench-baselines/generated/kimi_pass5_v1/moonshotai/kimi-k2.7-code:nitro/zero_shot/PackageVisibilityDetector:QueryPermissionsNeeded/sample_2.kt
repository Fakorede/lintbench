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

    override fun getApplicableCallNames(): List<String> =
        listOf("getInstalledPackages", "getInstalledApplications")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        descriptor: PsiMethod
    ) {
        val containingClass = descriptor.containingClass ?: return
        if (!context.evaluator.extendsClass(
                containingClass,
                "android.content.pm.PackageManager",
                false
            )
        ) {
            return
        }

        val message = "This call is affected by package visibility changes on Android 11+. " +
                "Consider adding a `<queries>` declaration to the manifest or using " +
                "`PackageManager#getPackageInfo` / `#queryIntentActivities` to query specific apps. " +
                "See https://g.co/dev/packagevisibility."

        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            message
        )
    }

    companion object {
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default.
                If you need to query or interact with other installed apps, you may need to add a
                `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and
                `PackageManager#getInstalledApplications` will no longer return information about all
                installed apps. To query specific apps or types of apps, you can use methods like
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                Reference documentation: https://g.co/dev/packagevisibility
            """.trimIndent(),
            category = Category.ANDROID,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}