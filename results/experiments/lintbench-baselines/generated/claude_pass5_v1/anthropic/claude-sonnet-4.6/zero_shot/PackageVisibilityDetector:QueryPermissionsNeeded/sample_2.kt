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
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps \
                by default. If you need to query or interact with other installed apps, you may need \
                to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                Reference documentation:
                - https://g.co/dev/packagevisibility
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

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private val AFFECTED_METHODS = setOf(
            "getInstalledPackages",
            "getInstalledApplications"
        )

        private val MESSAGE_MAP = mapOf(
            "getInstalledPackages" to
                "`PackageManager#getInstalledPackages` will not return information about all " +
                "installed apps on Android 11+. Consider adding a `<queries>` declaration to " +
                "your manifest, or use `PackageManager#getPackageInfo` to query specific apps.",
            "getInstalledApplications" to
                "`PackageManager#getInstalledApplications` will not return information about all " +
                "installed apps on Android 11+. Consider adding a `<queries>` declaration to " +
                "your manifest, or use `PackageManager#queryIntentActivities` to query specific " +
                "types of apps."
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return AFFECTED_METHODS.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != PACKAGE_MANAGER_CLASS) {
            return
        }

        val methodName = method.name
        if (methodName !in AFFECTED_METHODS) {
            return
        }

        val message = MESSAGE_MAP[methodName]
            ?: "This method is affected by package visibility restrictions in Android 11+. " +
               "Consider adding a `<queries>` declaration in your manifest."

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getNameLocation(node),
            message = message
        )
    }

    override fun beforeCheckFile(context: Context) {
        // No-op: we handle checks in visitMethodCall
    }
}