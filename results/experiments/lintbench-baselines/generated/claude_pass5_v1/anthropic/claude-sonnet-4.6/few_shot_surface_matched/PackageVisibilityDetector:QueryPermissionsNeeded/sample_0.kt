package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.detector.api.Category
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
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    // Track whether the manifest has a <queries> element
    private var hasQueriesElement = false

    // Track whether the manifest has QUERY_ALL_PACKAGES permission
    private var hasQueryAllPackagesPermission = false

    companion object {
        private const val TARGET_SDK_VERSION = 30 // Android 11

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val TAG_QUERIES = "queries"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        private val APPLICABLE_METHOD_NAMES = listOf(
            "getInstalledPackages",
            "getInstalledApplications"
        )

        private const val KEY_METHOD = "method"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation =
                """
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
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }

    // --- XmlScanner ---

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
                if (name == QUERY_ALL_PACKAGES) {
                    hasQueryAllPackagesPermission = true
                }
            }
        }
    }

    // --- SourceCodeScanner ---

    override fun getApplicableMethodNames(): List<String> {
        return APPLICABLE_METHOD_NAMES
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val methodName = method.name
        val message = when (methodName) {
            "getInstalledPackages" ->
                "`PackageManager.getInstalledPackages` will not return information about all " +
                    "installed apps if your app targets Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or use " +
                    "`PackageManager.getPackageInfo` for specific package queries."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about " +
                    "all installed apps if your app targets Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or use " +
                    "`PackageManager.queryIntentActivities` for specific app queries."
            else -> return
        }

        val location = context.getLocation(node)
        val incident = Incident(ISSUE, node, location, message)
        val map = map().put(KEY_METHOD, methodName)
        context.report(incident, targetSdkAtLeast(TARGET_SDK_VERSION), map)
    }

    override fun filterIncident(context: JavaContext, incident: Incident, map: LintMap): Boolean {
        // Suppress the incident if the manifest already has a <queries> element
        // or the QUERY_ALL_PACKAGES permission declared
        if (hasQueriesElement || hasQueryAllPackagesPermission) {
            return false
        }
        return true
    }
}