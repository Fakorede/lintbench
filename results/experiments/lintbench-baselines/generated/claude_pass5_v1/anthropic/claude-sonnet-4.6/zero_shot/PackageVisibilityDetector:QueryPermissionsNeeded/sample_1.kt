package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

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
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST)
            ),
            moreInfo = "https://g.co/dev/packagevisibility"
        )

        private val AFFECTED_METHODS = setOf(
            "getInstalledPackages",
            "getInstalledApplications"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return AFFECTED_METHODS.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val methodName = method.name
        if (methodName !in AFFECTED_METHODS) {
            return
        }

        val message = when (methodName) {
            "getInstalledPackages" ->
                "`PackageManager.getInstalledPackages` will not return information about all " +
                    "installed apps if targeting Android 11+. Consider adding a `<queries>` " +
                    "declaration in your manifest or using `PackageManager.getPackageInfo` " +
                    "or `PackageManager.queryIntentActivities` instead."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed apps if targeting Android 11+. Consider adding a `<queries>` " +
                    "declaration in your manifest or using `PackageManager.getPackageInfo` " +
                    "or `PackageManager.queryIntentActivities` instead."
            else ->
                "This `PackageManager` method may not return information about all installed " +
                    "apps if targeting Android 11+. Consider adding a `<queries>` declaration " +
                    "in your manifest."
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getNameLocation(node),
            message = message
        )
    }
}