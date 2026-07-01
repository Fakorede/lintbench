package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(
                method,
                "android.content.pm.PackageManager"
            )
        ) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getCallLocation(
                node,
                includeReceiver = true,
                includeArguments = false
            ),
            "Using `${node.methodName}` is affected by package visibility changes on Android 11+; " +
                    "consider adding a `<queries>` declaration to your manifest or using targeted APIs " +
                    "such as `getPackageInfo` or `queryIntentActivities`."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps running on Android 11 (API 30) and higher cannot query or interact with
                other installed apps by default. Calls to `PackageManager#getInstalledPackages`
                or `PackageManager#getInstalledApplications` may no longer return all installed apps.

                If your app needs to query or interact with specific apps, add a `<queries>`
                declaration to your `AndroidManifest.xml` or use targeted APIs such as
                `getPackageInfo` or `queryIntentActivities`.

                See https://g.co/dev/packagevisibility for more details.
            """,
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