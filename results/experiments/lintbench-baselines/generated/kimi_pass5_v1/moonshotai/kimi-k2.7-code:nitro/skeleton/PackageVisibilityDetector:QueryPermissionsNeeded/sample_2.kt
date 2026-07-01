package com.android.tools.lint.checks

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
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_CONTENT_PM_PACKAGEMANAGER = "android.content.pm.PackageManager"
        private const val METHOD_GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val METHOD_GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val TAG_QUERIES = "queries"
        private const val KEY_QUERIES_DECLARED = "queries.declared"
        private const val ANDROID_11_API_LEVEL = 30

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 (API level 30) or higher cannot query or interact
                with other installed apps by default. Because of this change,
                `PackageManager#getInstalledPackages()` and `PackageManager#getInstalledApplications()`
                may no longer return information about all installed apps.

                If your app needs to query other installed apps, add an appropriate
                `<queries>` declaration to your AndroidManifest.xml. For more details, see
                https://g.co/dev/packagevisibility.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_QUERIES)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        context.driver.getPartialResults(context.mainProject, ISSUE)
            .putBoolean(KEY_QUERIES_DECLARED, true)
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        METHOD_GET_INSTALLED_PACKAGES,
        METHOD_GET_INSTALLED_APPLICATIONS,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (context.project.targetSdk < ANDROID_11_API_LEVEL) {
            return
        }

        if (method.containingClass?.qualifiedName != ANDROID_CONTENT_PM_PACKAGEMANAGER) {
            return
        }

        val message = when (method.name) {
            METHOD_GET_INSTALLED_PACKAGES ->
                "Calling `PackageManager#getInstalledPackages()` may return incomplete " +
                    "information on Android 11+; add a `<queries>` declaration if you need " +
                    "to see other installed apps."

            METHOD_GET_INSTALLED_APPLICATIONS ->
                "Calling `PackageManager#getInstalledApplications()` may return incomplete " +
                    "information on Android 11+; add a `<queries>` declaration if you need " +
                    "to see other installed apps."

            else -> return
        }

        val incident = Incident(
            ISSUE,
            context.getLocation(node),
            message,
        )
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return !map.getBoolean(KEY_QUERIES_DECLARED, false)
    }
}