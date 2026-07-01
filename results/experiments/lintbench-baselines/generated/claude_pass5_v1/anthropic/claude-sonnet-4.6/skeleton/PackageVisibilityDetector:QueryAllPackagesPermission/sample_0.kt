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
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), Detector.UastScannerCompat, Detector.XmlScannerCompat {

    companion object {
        private const val QUERY_ALL_PACKAGES = "QUERY_ALL_PACKAGES"
        private const val ATTR_NAME = "name"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val KEY_PERMISSION = "permission"
        private const val KEY_SOURCE = "source"
        private const val SOURCE_MANIFEST = "manifest"
        private const val SOURCE_CODE = "code"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in \
                order to see all installed apps is rarely necessary, and most apps on Google Play are \
                not allowed to have this permission.
                """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val ANDROID_PERMISSION_QUERY_ALL_PACKAGES =
            "android.permission.QUERY_ALL_PACKAGES"
    }

    // -----------------------------------------------------------------------
    // XML scanning – manifest uses-permission elements
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            ATTR_NAME
        ).ifEmpty {
            element.getAttribute(ATTR_NAME)
        }

        if (nameAttr == ANDROID_PERMISSION_QUERY_ALL_PACKAGES ||
            nameAttr == QUERY_ALL_PACKAGES
        ) {
            val incident = Incident(
                ISSUE,
                element,
                context.getLocation(element),
                "Using the `QUERY_ALL_PACKAGES` permission is rarely necessary and is " +
                    "not allowed for most apps on Google Play. Consider using a " +
                    "`<queries>` declaration in your manifest instead.",
            )
            val map = LintMap()
            map.put(KEY_SOURCE, SOURCE_MANIFEST)
            map.put(KEY_PERMISSION, nameAttr)
            context.report(incident, map)
        }
    }

    // -----------------------------------------------------------------------
    // Source code scanning – calls to getInstalledPackages / getInstalledApplications
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> =
        listOf(GET_INSTALLED_PACKAGES, GET_INSTALLED_APPLICATIONS)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only flag calls on PackageManager
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName != "android.content.pm.PackageManager") return

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling `${method.name}()` to retrieve all installed packages may require " +
                "the `QUERY_ALL_PACKAGES` permission. Consider using a `<queries>` " +
                "declaration in your manifest to specify which packages you need.",
        )
        val map = LintMap()
        map.put(KEY_SOURCE, SOURCE_CODE)
        map.put(KEY_PERMISSION, method.name)
        context.report(incident, map)
    }

    // -----------------------------------------------------------------------
    // Incident filtering – suppress if min SDK < 30 for code-side warnings
    // -----------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val source = map.getString(KEY_SOURCE, null)

        if (source == SOURCE_CODE) {
            // Package visibility restrictions were introduced in API 30.
            // If the project's minSdk is below 30 the call may be legitimate on older devices,
            // but we still flag it because the app may also run on API 30+ devices.
            // Return true to keep (report) the incident unconditionally.
            return true
        }

        // For manifest-declared QUERY_ALL_PACKAGES permission, always report.
        return true
    }
}