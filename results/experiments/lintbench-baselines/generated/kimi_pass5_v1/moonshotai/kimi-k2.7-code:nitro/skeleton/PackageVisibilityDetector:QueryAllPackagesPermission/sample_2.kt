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
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) != PERMISSION_QUERY_ALL_PACKAGES) {
            return
        }

        val message = "Declaring the QUERY_ALL_PACKAGES permission is not allowed for most apps " +
            "on Google Play. Use a <queries> declaration to interact with specific installed " +
            "apps instead."

        val map = LintMap.Builder().putBoolean(KEY_FROM_PERMISSION, true).build()
        context.report(ISSUE, element, context.getLocation(element), message, map)
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
        if (method.containingClass?.qualifiedName != PACKAGE_MANAGER_CLASS) {
            return
        }

        val map = LintMap.Builder().putString(KEY_CALL_METHOD, method.name).build()
        val message = "Calling PackageManager.${method.name} to enumerate all installed apps " +
            "typically requires QUERY_ALL_PACKAGES; use a <queries> declaration instead."

        context.report(ISSUE, node, context.getLocation(node), message, map)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (map.getBoolean(KEY_FROM_PERMISSION) == true) {
            return true
        }

        if (map.getString(KEY_CALL_METHOD) != null) {
            return !hasQueriesDeclaration(context)
        }

        return true
    }

    private fun hasQueriesDeclaration(context: Context): Boolean {
        val manifest = context.mainProject.manifest?.takeIf { it.isFile } ?: return false

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(manifest)
            document.getElementsByTagName(TAG_QUERIES).length > 0
        } catch (ignored: Exception) {
            false
        }
    }

    companion object {
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_QUERIES = "queries"
        private const val ATTR_NAME = "name"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val PERMISSION_QUERY_ALL_PACKAGES =
            "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val METHOD_GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val METHOD_GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val KEY_FROM_PERMISSION = "fromPermission"
        private const val KEY_CALL_METHOD = "callMethod"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If your app needs to query or interact with other installed apps, you should use a
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission to
                see all installed apps is rarely necessary, and most apps on Google Play are not
                allowed to have this permission.

                See https://g.co/dev/packagevisibility for details.
            """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }
}