package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(com.android.SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attribute = element.getAttributeNodeNS(
            com.android.SdkConstants.ANDROID_URI,
            com.android.SdkConstants.ATTR_NAME
        ) ?: return

        if (attribute.value == "android.permission.QUERY_ALL_PACKAGES") {
            val incident = Incident(
                ISSUE,
                element,
                context.getLocation(attribute),
                "Using the `QUERY_ALL_PACKAGES` permission in order to see all installed apps is rarely necessary. Use a `<queries>` declaration instead."
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
            "queryBroadcastReceivers"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Consider using a `<queries>` declaration in your manifest instead of querying all packages or using `QUERY_ALL_PACKAGES`."
            )
            context.report(incident)
        }
    }

    override fun filterIncident(incident: Incident): Boolean {
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
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}