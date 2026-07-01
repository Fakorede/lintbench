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
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasQueryMethodCall = false

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        if (permissionName == "android.permission.QUERY_ALL_PACKAGES") {
            val incident = Incident(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Using the QUERY_ALL_PACKAGES permission is rarely necessary; use `<queries>` instead"
            )
            context.report(incident)
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "getInstalledPackages",
            "getInstalledApplications",
            "queryIntentActivities",
            "queryIntentServices",
            "queryBroadcastReceivers",
            "queryIntentContentProviders",
            "getPackageInfo"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            hasQueryMethodCall = true
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (hasQueryMethodCall) {
            incident.message = "Using the QUERY_ALL_PACKAGES permission is rarely necessary. " +
                    "Since you are querying packages (e.g. calling PackageManager APIs), " +
                    "you should use a `<queries>` declaration in your manifest instead."
        } else {
            incident.message = "Using the QUERY_ALL_PACKAGES permission is rarely necessary; " +
                    "your app does not appear to call any PackageManager APIs that require package visibility."
        }
        return true
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in \
                order to see all installed apps is rarely necessary, and most apps on Google Play are \
                not allowed to have this permission.
                """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}