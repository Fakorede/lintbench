package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
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
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
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

        private val AFFECTED_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
        )

        private const val QUERIES_PERMISSION =
            "android.permission.QUERY_ALL_PACKAGES"

        private const val KEY_METHOD_NAME = "methodName"
        private const val KEY_HAS_PERMISSION = "hasPermission"

        // We store whether the manifest declares QUERY_ALL_PACKAGES permission
        private var hasQueryAllPackagesPermission = false
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permissionName == QUERIES_PERMISSION) {
            hasQueryAllPackagesPermission = true
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
                    "`<queries>` declaration to your manifest or use " +
                    "`PackageManager.getPackageInfo` instead."
            "getInstalledApplications" ->
                "`PackageManager.getInstalledApplications` will not return information about all " +
                    "installed applications when targeting Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest or use " +
                    "`PackageManager.getPackageInfo` instead."
            else ->
                "This `PackageManager` method will not return information about all installed " +
                    "packages when targeting Android 11+. Consider adding a `<queries>` " +
                    "declaration to your manifest."
        }

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        val map = map()
        map.put(KEY_METHOD_NAME, methodName)
        context.report(incident, map)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // If the app has declared QUERY_ALL_PACKAGES permission, suppress the warning
        if (hasQueryAllPackagesPermission) {
            return false
        }

        // Only report if targeting Android 11 (API 30) or higher
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        if (targetSdk < 30) {
            return false
        }

        return true
    }
}