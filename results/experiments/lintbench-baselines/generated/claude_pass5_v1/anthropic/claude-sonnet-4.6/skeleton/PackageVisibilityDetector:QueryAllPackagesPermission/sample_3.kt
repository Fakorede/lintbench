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

class PackageVisibilityDetector : Detector(), Detector.SourceCodeScanner, Detector.XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES = "QUERY_ALL_PACKAGES"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ATTR_NAME = "name"
        private const val ANDROID_PERMISSION_QUERY_ALL_PACKAGES =
            "android.permission.QUERY_ALL_PACKAGES"

        private const val KEY_REQUIRES_API = "requiresApi"
        private const val MIN_SDK_FOR_QUERY_ALL = 30

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using \
                a `<queries>` declaration in your manifest. Using the `QUERY_ALL_PACKAGES` \
                permission in order to see all installed apps is rarely necessary, and most apps \
                on Google Play are not allowed to have this permission.

                See https://g.co/dev/packagevisibility for details.
            """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val METHOD_GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val METHOD_GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
    }

    // -------------------------------------------------------------------------
    // XmlScanner — manifest <uses-permission> elements
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            ATTR_NAME
        ).ifEmpty {
            element.getAttribute(ATTR_NAME)
        }

        if (name == ANDROID_PERMISSION_QUERY_ALL_PACKAGES) {
            val incident = Incident(
                ISSUE,
                element,
                context.getLocation(element),
                "Use the `<queries>` manifest declaration instead of `QUERY_ALL_PACKAGES`",
            )
            // Store the minSdkVersion so filterIncident can decide later
            context.report(incident, map().put(KEY_REQUIRES_API, MIN_SDK_FOR_QUERY_ALL))
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — PackageManager API calls
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        METHOD_GET_INSTALLED_PACKAGES,
        METHOD_GET_INSTALLED_APPLICATIONS,
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
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            "Calling `${method.name}` returns results for all installed packages and " +
                "requires the `QUERY_ALL_PACKAGES` permission on API $MIN_SDK_FOR_QUERY_ALL+. " +
                "Consider using a `<queries>` declaration in your manifest instead.",
        )
        context.report(incident, map().put(KEY_REQUIRES_API, MIN_SDK_FOR_QUERY_ALL))
    }

    // -------------------------------------------------------------------------
    // filterIncident — suppress if minSdk is below the threshold
    // -------------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val requiredApi = map.getInt(KEY_REQUIRES_API) ?: MIN_SDK_FOR_QUERY_ALL
        val project = context.mainProject
        val minSdk = project.minSdk
        // Only report if the app targets an SDK where the restriction applies
        return minSdk >= requiredApi
    }
}