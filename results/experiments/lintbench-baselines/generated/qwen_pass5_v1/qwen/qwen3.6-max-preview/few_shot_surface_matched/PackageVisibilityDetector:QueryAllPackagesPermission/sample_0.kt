package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
import org.w3c.dom.Element
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), XmlScanner, SourceCodeScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "name") ?: return
        if (attr.value == "android.permission.QUERY_ALL_PACKAGES") {
            context.report(
                Incident(
                    ISSUE,
                    element,
                    context.getValueLocation(attr),
                    "Using the QUERY_ALL_PACKAGES permission is rarely necessary and restricted on Google Play. Use the <queries> element instead."
                )
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("checkSelfPermission", "checkPermission", "requestPermissions")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        for (arg in node.valueArguments) {
            val value = context.evaluator.getStringValue(arg)
            if (value == "android.permission.QUERY_ALL_PACKAGES") {
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getLocation(arg),
                        "Using the QUERY_ALL_PACKAGES permission is rarely necessary and restricted on Google Play. Use the <queries> element instead."
                    )
                )
                return
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        return true
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = "If you need to query or interact with other installed apps, you should be using a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely necessary, and most apps on Google Play are not allowed to have this permission. See https://g.co/dev/packagevisibility",
            category = Category.COMPLIANCE,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)
            )
        )
    }
}