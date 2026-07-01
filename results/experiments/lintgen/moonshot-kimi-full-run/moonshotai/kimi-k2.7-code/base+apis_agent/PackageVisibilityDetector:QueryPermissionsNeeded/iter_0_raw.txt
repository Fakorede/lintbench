package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.JavaElementVisitor

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: JavaElementVisitor?
    ) {
        val method = node.resolve() ?: return
        val containingClass = method.containingClass ?: return
        if (context.evaluator.extendsClass(
                containingClass,
                "android.content.pm.PackageManager",
                false
            )
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `${node.methodName}` may be affected by package visibility " +
                    "filtering on Android 11+. Consider adding a `<queries>` declaration " +
                    "to your manifest or using targeted APIs such as `getPackageInfo` or " +
                    "`queryIntentActivities`."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps targeting Android 11 (API 30) cannot query or interact with other
                installed apps by default. Methods such as
                `PackageManager#getInstalledPackages` and
                `PackageManager#getInstalledApplications` no longer return information
                about all installed apps. If your app needs access to other packages,
                add a `<queries>` declaration to your manifest or use more targeted APIs
                such as `getPackageInfo` or `queryIntentActivities`.
                """.trimIndent(),
            moreInfo = "https://g.co/dev/packagevisibility",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}