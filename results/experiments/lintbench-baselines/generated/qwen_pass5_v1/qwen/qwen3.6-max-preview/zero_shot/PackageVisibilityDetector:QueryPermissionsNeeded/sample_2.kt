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
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "android.content.pm.PackageManager") {
            return
        }

        val message = "Using `${method.name}` on Android 11+ (API 30+) requires a `<queries>` declaration in your manifest due to package visibility restrictions. " +
                "Consider using `getPackageInfo`, `queryIntentActivities`, or adding `<queries>`."

        context.report(
            ISSUE,
            node,
            context.getNameLocation(node),
            message
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by package visibility restrictions",
            explanation = """
                Apps that target Android 11 (API 30) or higher cannot query or interact with other installed apps by default. \
                If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest. \
                As a corollary, the methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer \
                return information about all installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`. \
                Reference: https://g.co/dev/packagevisibility
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }
}