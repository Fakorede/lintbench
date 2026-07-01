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
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed \
                apps by default. If you need to query or interact with other installed apps, \
                you may need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information \
                about all installed apps. To query specific apps or types of apps, you can \
                use methods like `PackageManager#getPackageInfo` or \
                `PackageManager#queryIntentActivities`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://g.co/dev/packagevisibility"
        )

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private val FLAGGED_METHODS = setOf(
            "getInstalledPackages",
            "getInstalledApplications"
        )
    }

    override fun getApplicableMethodNames(): List<String> = FLAGGED_METHODS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, PACKAGE_MANAGER_CLASS, false)) {
            return
        }

        val methodName = method.name
        val message = when (methodName) {
            "getInstalledPackages" ->
                "`PackageManager.getInstalledPackages` will not return information about all " +
                    "installed packages when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or using " +
                    "`PackageManager.getPackageInfo` for specific packages."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed applications when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or using " +
                    "`PackageManager.queryIntentActivities` for specific app types."
            else ->
                "This `PackageManager` method is affected by package visibility changes in " +
                    "Android 11. Consider adding a `<queries>` declaration to your manifest."
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = false, includeArguments = false),
            message = message
        )
    }
}