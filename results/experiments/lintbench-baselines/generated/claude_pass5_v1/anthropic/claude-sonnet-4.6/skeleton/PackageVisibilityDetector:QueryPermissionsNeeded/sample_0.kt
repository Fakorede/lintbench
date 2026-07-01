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

        private val FLAGGED_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
        )

        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        private const val KEY_METHOD_NAME = "methodName"
        private const val KEY_HAS_QUERIES_TAG = "hasQueriesTag"
        private const val KEY_HAS_QUERY_ALL_PACKAGES = "hasQueryAllPackages"

        private const val MIN_TARGET_SDK = 30
    }

    /**
     * Whether the manifest contains a <queries> element.
     */
    private var hasQueriesTag = false

    /**
     * Whether the manifest declares the QUERY_ALL_PACKAGES permission.
     */
    private var hasQueryAllPackagesPermission = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_QUERIES, TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_QUERIES -> {
                hasQueriesTag = true
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
        return FLAGGED_METHODS
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
                    "`<queries>` declaration to your manifest, or use " +
                    "`PackageManager.getPackageInfo` to query for specific packages."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed applications when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest, or use " +
                    "`PackageManager.getPackageInfo` to query for specific packages."
            else -> return
        }

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        val map = map()
        map.put(KEY_METHOD_NAME, methodName)
        map.put(KEY_HAS_QUERIES_TAG, hasQueriesTag)
        map.put(KEY_HAS_QUERY_ALL_PACKAGES, hasQueryAllPackagesPermission)
        context.report(incident, map)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report the incident if the app targets Android 11 (API 30) or higher
        val targetSdk = context.mainProject.targetSdk
        if (targetSdk < MIN_TARGET_SDK) {
            return false
        }

        // If the manifest already declares QUERY_ALL_PACKAGES permission, suppress the warning
        val hasQueryAll = map.getBoolean(KEY_HAS_QUERY_ALL_PACKAGES) ?: false
        if (hasQueryAll) {
            return false
        }

        // If the manifest already has a <queries> tag, suppress the warning
        val hasQueries = map.getBoolean(KEY_HAS_QUERIES_TAG) ?: false
        if (hasQueries) {
            return false
        }

        return true
    }
}