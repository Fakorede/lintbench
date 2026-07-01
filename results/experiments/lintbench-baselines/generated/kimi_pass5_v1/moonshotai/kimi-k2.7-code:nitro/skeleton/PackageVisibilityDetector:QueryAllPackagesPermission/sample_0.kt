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
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private const val PERMISSION_MESSAGE =
            "Using the QUERY_ALL_PACKAGES permission is rarely necessary; prefer <queries> declarations"
        private const val QUERY_MESSAGE =
            "This call queries all installed apps and may require QUERY_ALL_PACKAGES; prefer <queries> declarations"

        private val EXPLANATION =
            "The <code>QUERY_ALL_PACKAGES</code> permission allows an app to see all installed apps. " +
                "Most apps do not need this and are not allowed to use it on Google Play.<br/>" +
                "Instead, use a <code>&lt;queries&gt;</code> declaration in the manifest to access only " +
                "the packages and intent filters your app requires. " +
                "See <a href=\"https://g.co/dev/packagevisibility\">Package visibility filtering on Android</a>."

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = EXPLANATION,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        "uses-permission",
        "uses-permission-sdk-23",
    )

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (QUERY_ALL_PACKAGES_PERMISSION == name) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                PERMISSION_MESSAGE,
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getInstalledPackages",
        "getInstalledApplications",
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            QUERY_MESSAGE,
        )
    }

    override fun filterIncident(
        context: Context,
        incident: Incident,
        map: LintMap,
    ): Boolean {
        return context.mainProject.targetSdkVersion.featureLevel >= 30
    }
}