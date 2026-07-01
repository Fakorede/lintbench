package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUElementHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName != "getInstalledPackages" && methodName != "getInstalledApplications") {
                    return
                }

                val targetSdk = context.project.targetSdk
                if (targetSdk != -1 && targetSdk < 30) {
                    return
                }

                val receiverType = node.receiverType
                val receiverClass = receiverType?.let { context.evaluator.getTypeClass(it) }
                if (receiverClass != null &&
                    context.evaluator.extendsClass(
                        receiverClass,
                        "android.content.pm.PackageManager",
                        false
                    )
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `PackageManager#getInstalledPackages` or `PackageManager#getInstalledApplications` " +
                                "will not return all installed apps when targeting Android 11 (API 30) or higher. " +
                                "Consider adding a `<queries>` declaration to the manifest or using " +
                                "`getPackageInfo`/`queryIntentActivities` instead."
                    )
                }
            }
        }

    companion object {
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default.
                As a result, `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications`
                will no longer return information about all installed apps. To query specific apps or types
                of apps, add a `<queries>` declaration to the manifest or use methods like
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.

                See https://g.co/dev/packagevisibility for more details.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}