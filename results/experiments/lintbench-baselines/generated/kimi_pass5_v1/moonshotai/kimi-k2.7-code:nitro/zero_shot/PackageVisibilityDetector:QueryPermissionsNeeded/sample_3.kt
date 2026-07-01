package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: org.jetbrains.uast.visitor.UastVisitor?
    ) {
        val method = node.resolve() ?: return
        val containingClass = method.containingClass?.qualifiedName ?: return

        if (containingClass == "android.content.pm.PackageManager") {
            val location = context.getCallLocation(
                node,
                includeReceiver = false,
                includeArguments = false
            )
            context.report(
                ISSUE,
                node,
                location,
                "Using `PackageManager#${method.name}` may be affected by package visibility restrictions on Android 11+."
            )
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. \
                If you need to query or interact with other installed apps, you may need to add a `<queries>` \
                declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all installed apps. \
                To query specific apps or types of apps, you can use methods like `PackageManager#getPackageInfo` or \
                `PackageManager#queryIntentActivities`.

                Reference documentation: https://g.co/dev/packagevisibility
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}