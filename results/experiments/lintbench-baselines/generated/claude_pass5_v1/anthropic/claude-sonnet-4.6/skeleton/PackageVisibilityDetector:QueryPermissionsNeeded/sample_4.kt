package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
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
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://g.co/dev/packagevisibility",
        )

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private const val METHOD_GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val METHOD_GET_INSTALLED_APPLICATIONS = "getInstalledApplications"

        private const val TAG_QUERIES = "queries"
        private const val TAG_USES_PERMISSION = "uses-permission"

        private const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        private const val KEY_METHOD_NAME = "methodName"
        private const val KEY_HAS_QUERY_ALL_PACKAGES = "hasQueryAllPackages"
        private const val KEY_HAS_QUERIES_ELEMENT = "hasQueriesElement"

        // Minimum target SDK where package visibility filtering applies
        private const val MIN_TARGET_SDK = 30
    }

    // Track whether the manifest has a <queries> element or QUERY_ALL_PACKAGES permission
    private var hasQueriesElement = false
    private var hasQueryAllPackagesPermission = false

    override fun beforeCheckRootProject(context: Context) {
        hasQueriesElement = false
        hasQueryAllPackagesPermission = false
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_QUERIES, TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_QUERIES -> {
                hasQueriesElement = true
            }
            TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == PERMISSION_QUERY_ALL_PACKAGES) {
                    hasQueryAllPackagesPermission = true
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(METHOD_GET_INSTALLED_PACKAGES, METHOD_GET_INSTALLED_APPLICATIONS)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val methodName = method.name
        val message = when (methodName) {
            METHOD_GET_INSTALLED_PACKAGES ->
                "`PackageManager.getInstalledPackages` will not return information about all " +
                    "installed packages when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or use more specific query methods."
            METHOD_GET_INSTALLED_APPLICATIONS ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed applications when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or use more specific query methods."
            else -> return
        }

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        val lintMap = LintMap()
        lintMap.put(KEY_METHOD_NAME, methodName)
        lintMap.put(KEY_HAS_QUERY_ALL_PACKAGES, hasQueryAllPackagesPermission)
        lintMap.put(KEY_HAS_QUERIES_ELEMENT, hasQueriesElement)
        context.report(incident, map = lintMap)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Check if the target SDK is high enough for the restriction to apply
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        if (targetSdk < MIN_TARGET_SDK) {
            // Below Android 11, package visibility filtering doesn't apply
            return false
        }

        // If the app already has QUERY_ALL_PACKAGES permission, it can see all packages
        val hasQueryAll = map.getBoolean(KEY_HAS_QUERY_ALL_PACKAGES) ?: false
        if (hasQueryAll) {
            return false
        }

        // If the app already has a <queries> element, it's aware of the issue
        // and has taken steps to declare what it needs. Still warn since
        // getInstalledPackages/getInstalledApplications won't return everything.
        // We still report the incident in this case as the method calls themselves
        // are still affected.

        return true
    }
}