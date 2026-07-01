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

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(
                containingClass,
                "android.content.pm.PackageManager",
                false
            )
        ) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "This call is affected by package visibility changes on Android 11+; " +
                    "it will not return all installed apps unless a `<queries>` " +
                    "declaration is added to the manifest."
        )
    }

    companion object {
        private const val ISSUE_ID = "QueryPermissionsNeeded"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = ISSUE_ID,
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer return information about all installed apps. To query specific apps or types of apps, you can use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                See https://g.co/dev/packagevisibility for more details.
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