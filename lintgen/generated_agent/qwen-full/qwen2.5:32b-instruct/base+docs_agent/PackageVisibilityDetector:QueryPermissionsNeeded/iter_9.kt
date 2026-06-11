package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Node

class PackageVisibilityDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        val PACKAGE_VISIBILITY_ISSUE = Issue.create(
            id = "PackageVisibility",
            briefDescription = "Using APIs affected by package visibility changes",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest.
                
                As a corollary, the methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer return information about all installed apps. To query specific apps or types of apps, you can use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            url = "https://g.co/dev/packagevisibility"
        )
    }

    override fun getApplicableMethodNames() = listOf("getInstalledPackages", "getInstalledApplications")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val methodName = context.evaluator.getMethodName(node)
        if (methodName == "getInstalledPackages" || methodName == "getInstalledApplications") {
            context.report(
                PACKAGE_VISIBILITY_ISSUE,
                node,
                context.getLocation(node),
                "Using $methodName is affected by package visibility changes in Android 11. Consider using `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities` instead."
            )
        }
    }

    override fun afterCheckProject(context: Context) {
        val manifestFile = context.getProjectManifest()
        if (manifestFile != null) {
            val targetSdkVersion = context.getManifest().getTargetSdkVersion() ?: 0
            if (targetSdkVersion >= 30) {
                val queriesNode = findQueriesNode(manifestFile.documentElement)
                if (queriesNode == null) {
                    context.report(
                        PACKAGE_VISIBILITY_ISSUE,
                        manifestFile,
                        context.getLocation(manifestFile.documentElement),
                        "Apps targeting Android 11 or higher should declare a <queries> element in the manifest to query other installed apps."
                    )
                }
            }
        }
    }

    @VisibleForTesting
    fun findQueriesNode(node: Node): Node? {
        val childNodes = node.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeName == "queries") return child
        }
        return null
    }

    override fun getApplicableAttributes() = listOf("android:targetSdkVersion")
}