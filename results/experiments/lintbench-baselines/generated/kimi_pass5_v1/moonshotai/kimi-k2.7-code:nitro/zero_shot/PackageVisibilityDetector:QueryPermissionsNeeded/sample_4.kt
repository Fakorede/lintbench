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
import org.jetbrains.uast.UElement

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        node: UElement
    ) {
        val method = node as? PsiMethod ?: return
        if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        val message = buildString {
            append(
                "This method may not return all installed apps on Android 11+ (targetSdkVersion 30+) "
            )
            append("due to package visibility changes. ")
            append(
                "Consider adding <queries> declarations to the manifest or using targeted APIs such as "
            )
            append("PackageManager#getPackageInfo or PackageManager#queryIntentActivities.")
        }

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
                Apps that target Android 11 and higher cannot query or interact with other
                installed apps by default. As a result, `PackageManager#getInstalledPackages` and
                `PackageManager#getInstalledApplications` may return incomplete information.

                To query specific apps, add a `<queries>` declaration to your manifest or use
                targeted APIs such as `PackageManager#getPackageInfo` or
                `PackageManager#queryIntentActivities`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }
}