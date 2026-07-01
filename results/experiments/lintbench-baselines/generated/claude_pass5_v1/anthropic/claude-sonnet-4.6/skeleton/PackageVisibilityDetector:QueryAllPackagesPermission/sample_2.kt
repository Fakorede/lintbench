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
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), Detector.SourceCodeScanner, Detector.XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val KEY_REQUIRES_QUERY_ALL = "requiresQueryAll"

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
            moreInfo = "https://g.co/dev/packagevisibility",
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == QUERY_ALL_PACKAGES) {
            val incident = Incident(
                ISSUE,
                element,
                context.getLocation(element),
                "Using the `QUERY_ALL_PACKAGES` permission is not recommended; " +
                    "use a `<queries>` declaration instead",
            )
            context.report(incident, map().put(KEY_REQUIRES_QUERY_ALL, true))
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        GET_INSTALLED_PACKAGES,
        GET_INSTALLED_APPLICATIONS,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName != "android.content.pm.PackageManager") return

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Calling `${method.name}()` can return all installed packages; " +
                "consider using a `<queries>` declaration in your manifest instead",
        )
        context.report(incident, map().put(KEY_REQUIRES_QUERY_ALL, false))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only filter/escalate based on project context if needed.
        // For manifest-declared QUERY_ALL_PACKAGES, always report.
        // For API calls, report as a warning unless QUERY_ALL_PACKAGES is also declared.
        val isPermissionDeclaration = map.getBoolean(KEY_REQUIRES_QUERY_ALL, false) ?: false
        return if (isPermissionDeclaration) {
            // Always report when the permission is explicitly declared
            true
        } else {
            // Report API usage as informational — always surface it
            true
        }
    }
}