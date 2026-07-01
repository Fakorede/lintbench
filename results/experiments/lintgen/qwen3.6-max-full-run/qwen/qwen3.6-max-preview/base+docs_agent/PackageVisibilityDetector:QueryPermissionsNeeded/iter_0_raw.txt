package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. \
                If you need to query or interact with other installed apps, you may need to add a `<queries>` \
                declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                Reference documentation: https://g.co/dev/packagevisibility
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            val message = "Using `${method.name}` may not return all installed apps on Android 11+ due to " +
                    "package visibility changes. Consider adding a `<queries>` declaration in your manifest " +
                    "or using alternative APIs like `queryIntentActivities`."
            context.report(ISSUE, node, context.getNameLocation(node), message)
        }
    }
}