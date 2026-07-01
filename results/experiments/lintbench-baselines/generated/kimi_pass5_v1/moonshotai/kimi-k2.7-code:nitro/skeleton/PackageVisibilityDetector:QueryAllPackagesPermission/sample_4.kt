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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val MANIFEST_TAG = "manifest"
        private const val USES_PERMISSION_TAG = "uses-permission"
        private const val QUERIES_TAG = "queries"
        private const val NAME_ATTR = "name"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            enumSetOf(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                Using the QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely
                necessary. If you need to query or interact with other installed apps, you should use a
                `<queries>` declaration in the manifest instead. Most apps on Google Play are not allowed
                to have this permission.

                For more details, see https://g.co/dev/packagevisibility.
            """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(USES_PERMISSION_TAG)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (context.file.name != "AndroidManifest.xml") return

        val name = element.getAttributeNS(ANDROID_URI, NAME_ATTR)
        if (QUERY_ALL_PACKAGES_PERMISSION == name) {
            val message = "The QUERY_ALL_PACKAGES permission is rarely necessary; use a `<queries>` declaration instead."
            context.report(ISSUE, element, context.getElementLocation(element), message)
        }
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf(GET_INSTALLED_PACKAGES, GET_INSTALLED_APPLICATIONS)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER)) return
        if (context.mainProject.targetSdkVersion.featureLevel < 30) return

        val methodName = method.name
        val message = "Calling `$methodName` on PackageManager can require querying all installed apps; " +
                "use a `<queries>` declaration instead of the QUERY_ALL_PACKAGES permission."
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return when {
            incident.location.file.name == "AndroidManifest.xml" -> true
            else -> !hasQueriesDeclaration(context)
        }
    }

    private fun hasQueriesDeclaration(context: Context): Boolean {
        val project = context.mainProject
        return project.manifestFiles.any { manifest ->
            val doc = XmlUtils.parseDocumentSilently(manifest, true)
            doc?.documentElement?.let { root ->
                root.tagName == MANIFEST_TAG && root.getElementsByTagName(QUERIES_TAG).length > 0
            } ?: false
        }
    }
}