package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getCallLocation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("getInstalledPackages", "getInstalledApplications")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        descriptor: PsiElement
    ) {
        if (context.project.targetSdkVersion?.isAtLeast(30) != true) {
            return
        }

        val method = call.resolve() ?: return
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, PACKAGE_MANAGER_CLASS, false)) {
            return
        }

        val methodName = call.methodName ?: return
        val message = "Use of $methodName is affected by package visibility restrictions on " +
                "Android 11+; add a `<queries>` declaration to the manifest or use " +
                "`getPackageInfo`/`queryIntentActivities` instead"

        val location = context.getCallLocation(
            call,
            includeReceiver = false,
            includeArguments = false
        )
        context.report(ISSUE, call, location, message)
    }

    companion object {
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps
                by default. If your app needs to query or interact with other apps, you may need
                to add a `<queries>` declaration in the `AndroidManifest.xml`.

                As a corollary, `PackageManager#getInstalledPackages` and
                `PackageManager#getInstalledApplications` will no longer return information about
                all installed apps. To query specific apps or types of apps, use methods such as
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
            """,
            moreInfo = "https://g.co/dev/packagevisibility",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}