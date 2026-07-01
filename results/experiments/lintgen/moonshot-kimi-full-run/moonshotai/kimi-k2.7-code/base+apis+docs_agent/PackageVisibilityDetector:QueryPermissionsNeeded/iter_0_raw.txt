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

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        val message = buildString {
            append("Using `${method.name}` may not return all installed apps when targeting Android 11 (API 30) or higher. ")
            append("Consider using `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities` instead, ")
            append("and add a `<queries>` declaration to your manifest if needed.")
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default.
                If you need to query or interact with other installed apps, you may need to add a `<queries>`
                declaration in your manifest.

                The methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications`
                will no longer return information about all installed apps. To query specific apps or types
                of apps, use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

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