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
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val MESSAGE = "Using the QUERY_ALL_PACKAGES permission or querying all installed apps is rarely necessary; prefer targeted <queries> declarations"
        private const val EXPLANATION = "If your app needs to query or interact with other installed apps, use a <queries> declaration in the manifest. Declaring QUERY_ALL_PACKAGES to see all installed apps is rarely necessary, and most apps on Google Play are not allowed to have this permission. See https://g.co/dev/packagevisibility."

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = EXPLANATION,
            moreInfo = "https://g.co/dev/packagevisibility",
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttribute("android:name")
            .ifBlank { element.getAttribute("name") }
        if (name == QUERY_ALL_PACKAGES && element.getAttribute("tools:node") != "remove") {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                MESSAGE,
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getInstalledPackages",
        "getInstalledApplications",
        "queryIntentActivities",
        "queryIntentServices",
        "queryBroadcastReceivers",
        "queryContentProviders",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != PACKAGE_MANAGER) return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            MESSAGE,
        )
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean = true
}