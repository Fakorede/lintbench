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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), Detector.SourceCodeScanner, Detector.XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES = "QUERY_ALL_PACKAGES"
        private const val ATTR_NAME = "name"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val ANDROID_PERMISSION_PREFIX = "android.permission."
        private const val FULL_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"

        private const val KEY_PERMISSION = "permission"

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
                a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES \
                permission in order to see all installed apps is rarely necessary, and most \
                apps on Google Play are not allowed to have this permission.
                """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
            moreInfo = "https://g.co/dev/packagevisibility",
        )

        private fun isQueryAllPackagesPermission(name: String): Boolean {
            return name == FULL_PERMISSION ||
                name == QUERY_ALL_PACKAGES ||
                name == "$ANDROID_PERMISSION_PREFIX$QUERY_ALL_PACKAGES"
        }
    }

    // -------------------------------------------------------------------------
    // XML scanning – manifest <uses-permission> elements
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            ATTR_NAME
        ).ifEmpty {
            element.getAttribute(ATTR_NAME)
        }

        if (isQueryAllPackagesPermission(name)) {
            val incident = Incident(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Most apps do not need the `QUERY_ALL_PACKAGES` permission; use a " +
                    "`<queries>` declaration in your manifest instead.",
            )
            incident.map[KEY_PERMISSION] = name
            context.report(incident)
        }
    }

    // -------------------------------------------------------------------------
    // Java/Kotlin source scanning – checkCallingOrSelfPermission / similar calls
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        "checkPermission",
        "checkCallingPermission",
        "checkSelfPermission",
        "checkCallingOrSelfPermission",
        "enforcePermission",
        "enforceCallingPermission",
        "enforceSelfPermission",
        "enforceCallingOrSelfPermission",
        "requestPermissions",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Look through all value arguments for a string literal that matches
        // the QUERY_ALL_PACKAGES permission.
        for (arg in node.valueArguments) {
            val value = (arg as? ULiteralExpression)?.evaluateString()
                ?: arg.evaluateString()
                ?: continue
            if (isQueryAllPackagesPermission(value)) {
                val incident = Incident(
                    ISSUE,
                    node,
                    context.getLocation(arg),
                    "Most apps do not need the `QUERY_ALL_PACKAGES` permission; use a " +
                        "`<queries>` declaration in your manifest instead.",
                )
                incident.map[KEY_PERMISSION] = value
                context.report(incident)
                return
            }
        }
    }

    // -------------------------------------------------------------------------
    // Conditional filtering – suppress if the app targets a low enough API
    // level where the restriction does not apply (API < 30).
    // -------------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // The QUERY_ALL_PACKAGES restriction was introduced in Android 11 (API 30).
        // If the app's targetSdkVersion is below 30, the permission is harmless and
        // we suppress the warning.
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk in 1 until 30) {
            return false
        }
        // Return true to keep (report) the incident.
        return true
    }
}