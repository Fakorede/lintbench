package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    // Track whether the manifest has a <queries> element declared
    // We use a simple flag per project; we store it in the LintMap via filterIncident.

    companion object {
        private const val TARGET_SDK_PACKAGE_VISIBILITY = 30 // Android 11

        private const val KEY_METHOD = "method"
        private const val QUERIES_TAG = "queries"

        private val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        // Methods that are affected by package visibility filtering
        private val AFFECTED_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
            "queryBroadcastReceivers",
            "queryContentProviders",
            "queryInstrumentation",
            "queryIntentActivities",
            "queryIntentActivityOptions",
            "queryIntentServices",
            "getPackageInfo",
            "getApplicationInfo",
            "resolveActivity",
            "resolveContentProvider",
            "resolveService",
            "getLaunchIntentForPackage",
            "getLeanbackLaunchIntentForPackage",
        )

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

                See https://g.co/dev/packagevisibility for more details.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                Scope.JAVA_FILE_SCOPE,
            ),
            androidSpecific = true,
            moreInfo = "https://g.co/dev/packagevisibility",
        )

        private const val KEY_HAS_QUERIES = "hasQueries"
    }

    // -------------------------------------------------------------------------
    // XmlScanner — detect <queries> element in the manifest
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf(QUERIES_TAG)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Nothing to report here; we just note that a <queries> element exists.
        // The presence is communicated via filterIncident through the LintMap.
        // We store a marker on the driver's client properties so filterIncident
        // can check it. Because filterIncident runs per-project after all files
        // are scanned, we use a simple project-level property.
        context.project.setProperty(KEY_HAS_QUERIES, "true")
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — detect calls to affected PackageManager APIs
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = AFFECTED_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val methodName = method.name
        val message = buildMessage(methodName)
        val location = context.getNameLocation(node)

        val incident = Incident(ISSUE, node, location, message)
        context.report(incident, targetSdkAtLeast(TARGET_SDK_PACKAGE_VISIBILITY), map().put(KEY_METHOD, methodName))
    }

    private fun buildMessage(methodName: String): String {
        return when (methodName) {
            "getInstalledPackages", "getInstalledApplications" ->
                "`PackageManager.$methodName` will not return information about all " +
                    "installed apps if your app targets Android 11+. Consider adding a " +
                    "`<queries>` declaration to your manifest, or use more targeted " +
                    "query methods such as `getPackageInfo` or `queryIntentActivities`. " +
                    "See https://g.co/dev/packagevisibility for details."
            else ->
                "Consider adding a `<queries>` declaration to your manifest when calling " +
                    "`PackageManager.$methodName` if you need results from other packages " +
                    "on Android 11+. See https://g.co/dev/packagevisibility for details."
        }
    }

    // -------------------------------------------------------------------------
    // filterIncident — suppress if a <queries> element is present in the manifest
    // -------------------------------------------------------------------------

    override fun filterIncident(context: com.android.tools.lint.detector.api.Context, incident: Incident, map: LintMap): Boolean {
        // If the project already has a <queries> declaration, suppress the warning.
        val hasQueries = context.project.getProperty(KEY_HAS_QUERIES) == "true"
        if (hasQueries) {
            return false // suppress — queries element is present
        }
        return true // report the incident
    }
}