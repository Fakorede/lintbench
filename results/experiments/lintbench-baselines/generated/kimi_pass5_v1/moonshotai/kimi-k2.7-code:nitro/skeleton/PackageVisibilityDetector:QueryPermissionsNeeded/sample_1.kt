package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TAG_QUERIES = "queries"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 (API level 30) or higher cannot query or interact with other installed apps by default.

                The methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` are affected by this change: they will no longer return information about all installed apps. If you need to query specific apps or apps that satisfy a particular intent filter, add a `<queries>` declaration to your `AndroidManifest.xml`. If you legitimately need to query all installed apps, request the `QUERY_ALL_PACKAGES` permission instead.

                See https://g.co/dev/packagevisibility for more details.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val projectsWithQueries = mutableSetOf<String>()
    private val projectsWithQueryAllPackages = mutableSetOf<String>()

    override fun getApplicableMethodNames(): List<String>? =
        listOf(GET_INSTALLED_PACKAGES, GET_INSTALLED_APPLICATIONS)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInSubClass(method, PACKAGE_MANAGER, false)) {
            return
        }

        val message = "This call is affected by package visibility filtering on Android 11 (API 30)+; " +
                "it may not return all installed apps. Consider using `getPackageInfo` or " +
                "`queryIntentActivities`, or add a `<queries>` declaration to your manifest."

        context.report(
            ISSUE,
            node,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            message,
        )
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_QUERIES, TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val projectPath = context.mainProject.dir.absolutePath

        when (element.tagName) {
            TAG_QUERIES -> projectsWithQueries.add(projectPath)
            TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    .ifEmpty { element.getAttribute("android:name") }
                if (name == QUERY_ALL_PACKAGES_PERMISSION) {
                    projectsWithQueryAllPackages.add(projectPath)
                }
            }
        }
    }

    override fun beforeCheckRootProject(context: Context) {
        projectsWithQueries.clear()
        projectsWithQueryAllPackages.clear()
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val projectPath = context.mainProject.dir.absolutePath
        val targetSdk = context.mainProject.targetSdk

        if (targetSdk != -1 && targetSdk < 30) {
            return false
        }

        if (projectPath in projectsWithQueries || projectPath in projectsWithQueryAllPackages) {
            return false
        }

        return true
    }
}