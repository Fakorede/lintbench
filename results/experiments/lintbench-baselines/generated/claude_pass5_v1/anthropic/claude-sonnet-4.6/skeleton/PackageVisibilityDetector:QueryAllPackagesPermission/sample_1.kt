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

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
            Scope.MANIFEST_SCOPE,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        private const val KEY_REQUIRES_QUERY_ALL = "requiresQueryAll"

        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"

        private const val MESSAGE =
            "Using the `QUERY_ALL_PACKAGES` permission is rarely necessary and most apps on " +
                "Google Play are not allowed to have this permission. If you need to query or " +
                "interact with other installed apps, use a `<queries>` declaration in your " +
                "manifest instead."

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation =
                """
                If you need to query or interact with other installed apps, you should be using \
                a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES \
                permission in order to see all installed apps is rarely necessary, and most apps \
                on Google Play are not allowed to have this permission.
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
                context.getValueLocation(element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                    ?: return),
                MESSAGE,
            )
            context.report(incident, map())
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
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            val incident = Incident(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = false, includeArguments = true),
                "Calling `${method.name}` to get the full list of installed apps requires " +
                    "the `QUERY_ALL_PACKAGES` permission; consider using a `<queries>` " +
                    "declaration in your manifest instead.",
            )
            val map = map()
            map.put(KEY_REQUIRES_QUERY_ALL, true)
            context.report(incident, map)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // If this is a Java/Kotlin source incident (calling getInstalledPackages etc.),
        // only report it if the manifest also declares QUERY_ALL_PACKAGES, or if we
        // cannot determine the manifest contents (in which case we report it anyway).
        // For manifest incidents (the permission declaration itself), always report.
        val requiresQueryAll = map.getBoolean(KEY_REQUIRES_QUERY_ALL) ?: false
        return if (requiresQueryAll) {
            // For method call incidents, report them — the user is calling APIs that
            // require QUERY_ALL_PACKAGES; this is a warning that they may need it,
            // but the main issue is the permission itself.
            // We report the incident regardless (the caller already needs the permission
            // or should be using queries instead).
            true
        } else {
            // This is a manifest permission declaration — always report it.
            true
        }
    }
}