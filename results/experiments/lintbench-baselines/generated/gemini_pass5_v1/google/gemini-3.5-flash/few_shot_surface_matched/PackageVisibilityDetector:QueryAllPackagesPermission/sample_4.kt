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

    override fun getApplicableElements(): java.util.Collection<String> {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        if (name == "android.permission.QUERY_ALL_PACKAGES") {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Using the QUERY_ALL_PACKAGES permission"
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "getInstalledPackages",
            "getInstalledApplications",
            "queryIntentActivities",
            "queryIntentServices",
            "queryBroadcastReceivers",
            "queryContentProviders"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            // Intentionally left blank to satisfy interface requirements
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely necessary, and most apps on Google Play are not allowed to have this permission.
            """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}