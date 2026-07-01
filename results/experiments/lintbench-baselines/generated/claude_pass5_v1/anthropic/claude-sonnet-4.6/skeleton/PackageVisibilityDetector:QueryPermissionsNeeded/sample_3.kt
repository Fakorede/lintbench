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
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST_FILE),
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

        private val FILTERED_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
        )

        // Manifest element names that indicate queries declarations
        private const val TAG_QUERIES = "queries"
        private const val TAG_USES_PERMISSION = "uses-permission"

        // Permission that grants full package visibility
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"

        // Key used to store/retrieve info in LintMap
        private const val KEY_REQUIRE_MIN_SDK = "minSdk"

        // Android 11 API level
        private const val ANDROID_11_API = 30
    }

    /**
     * Tracks whether the manifest has a <queries> element or the
     * QUERY_ALL_PACKAGES permission, which would suppress the warning.
     */
    private var hasQueriesElement = false
    private var hasQueryAllPackagesPermission = false

    override fun beforeCheckRootProject(context: Context) {
        hasQueriesElement = false
        hasQueryAllPackagesPermission = false
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_QUERIES,
        TAG_USES_PERMISSION,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_QUERIES -> {
                hasQueriesElement = true
            }
            TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == QUERY_ALL_PACKAGES_PERMISSION) {
                    hasQueryAllPackagesPermission = true
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = FILTERED_METHODS

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
                    "`<queries>` declaration to your manifest or use " +
                    "`PackageManager.getPackageInfo` for specific packages."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed apps when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest."
            else ->
                "This method is affected by package visibility restrictions in Android 11+. " +
                    "Consider adding a `<queries>` declaration to your manifest."
        }

        val incident = Incident(context, ISSUE)
            .message(message)
            .at(node)

        // Store the minimum SDK for later filtering in filterIncident
        context.report(incident, map().put(KEY_REQUIRE_MIN_SDK, ANDROID_11_API))
    }

    // -------------------------------------------------------------------------
    // filterIncident — called after all files have been processed
    // -------------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report if the app targets Android 11 (API 30) or higher
        val minSdk = map.getInt(KEY_REQUIRE_MIN_SDK, ANDROID_11_API) ?: ANDROID_11_API
        val targetSdk = context.mainProject.targetSdk

        if (targetSdk < minSdk) {
            // Not targeting Android 11+, no issue
            return false
        }

        // If the manifest already declares <queries> or QUERY_ALL_PACKAGES, suppress
        if (hasQueriesElement || hasQueryAllPackagesPermission) {
            return false
        }

        return true
    }
}