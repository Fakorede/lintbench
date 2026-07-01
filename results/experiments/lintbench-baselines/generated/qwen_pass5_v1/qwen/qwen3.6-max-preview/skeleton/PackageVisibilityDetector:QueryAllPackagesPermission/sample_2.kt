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
import org.jetbrains.uast.evaluate
import org.w3c.dom.Element
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val PERMISSION = "android.permission.QUERY_ALL_PACKAGES"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = "If you need to query or interact with other installed apps, you should be using a " +
                "<queries> declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in " +
                "order to see all installed apps is rarely necessary, and most apps on Google Play are " +
                "not allowed to have this permission.",
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name == PERMISSION) {
            context.report(
                Incident(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Using the QUERY_ALL_PACKAGES permission is restricted. " +
                        "Use a <queries> declaration instead."
                )
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "checkSelfPermission",
        "requestPermissions",
        "shouldShowRequestPermissionRationale"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        for (arg in node.valueArguments) {
            if (arg.evaluate() == PERMISSION) {
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using the QUERY_ALL_PACKAGES permission is restricted. " +
                            "Use a <queries> declaration instead."
                    )
                )
                break
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }
}