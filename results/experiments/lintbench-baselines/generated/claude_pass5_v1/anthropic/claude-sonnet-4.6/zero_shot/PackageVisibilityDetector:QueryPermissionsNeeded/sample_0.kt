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

                Reference documentation:
                - https://g.co/dev/packagevisibility
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.JAVA_FILE)
            )
        )

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private val AFFECTED_METHODS = setOf(
            "getInstalledPackages",
            "getInstalledApplications"
        )

        private const val MESSAGE =
            "Consider adding a `<queries>` declaration to your manifest when calling " +
                "this method; apps that target Android 11 and higher will not get results " +
                "for all packages. See https://g.co/dev/packagevisibility for details."
    }

    override fun getApplicableMethodNames(): List<String> {
        return AFFECTED_METHODS.toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!AFFECTED_METHODS.contains(method.name)) {
            return
        }

        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS, false)
        ) {
            return
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getCallLocation(node, includeReceiver = false, includeArguments = false),
            message = MESSAGE
        )
    }

    override fun afterCheckEachProject(context: Context) {
        // No additional project-level checks needed
    }
}