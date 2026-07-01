package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_QUERIES
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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
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
                Apps that target Android 11 cannot query or interact with other installed \
                apps by default. If you need to query or interact with other installed apps, \
                you may need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information \
                about all installed apps. To query specific apps or types of apps, you can \
                use methods like `PackageManager#getPackageInfo` or \
                `PackageManager#queryIntentActivities`.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://g.co/dev/packagevisibility",
        )

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private val AFFECTED_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
        )

        private const val KEY_REQUIRES_QUERIES = "requiresQueries"

        // XML tag for <uses-permission> with QUERY_ALL_PACKAGES
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        // Minimum target SDK that enforces package visibility filtering
        private const val MIN_TARGET_SDK = 30
    }

    /**
     * Whether the manifest has a <queries> element or the QUERY_ALL_PACKAGES permission,
     * which would satisfy the requirement.
     */
    private var hasQueriesElement = false
    private var hasQueryAllPackagesPermission = false

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
        return AFFECTED_METHODS
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
            "getInstalledPackages" ->
                "`PackageManager.getInstalledPackages` will not return information about all " +
                    "installed packages when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or using " +
                    "`PackageManager.getPackageInfo` instead."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed applications when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or using " +
                    "`PackageManager.getPackageInfo` instead."
            else ->
                "This method is affected by package visibility restrictions in Android 11+. " +
                    "Consider adding a `<queries>` declaration in your manifest."
        }

        val incident = Incident(context)
            .issue(ISSUE)
            .location(context.getLocation(node))
            .message(message)
            .scope(node)

        val lintMap = LintMap()
        lintMap.put(KEY_REQUIRES_QUERIES, true)

        context.report(incident, lintMap)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // If the manifest already has a <queries> element or QUERY_ALL_PACKAGES permission,
        // the developer has already addressed the issue.
        if (hasQueriesElement || hasQueryAllPackagesPermission) {
            return false
        }

        // Only report the incident if the app targets Android 11 (API 30) or higher.
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < MIN_TARGET_SDK) {
            return false
        }

        return map.getBoolean(KEY_REQUIRES_QUERIES) == true
    }
}