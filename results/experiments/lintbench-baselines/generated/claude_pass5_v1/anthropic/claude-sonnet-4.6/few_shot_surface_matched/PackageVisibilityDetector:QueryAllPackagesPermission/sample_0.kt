package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
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
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val TARGET_SDK_KEY = "targetSdk"
        private const val MIN_TARGET_SDK = 30

        private val EXPLANATION =
            """
            If you need to query or interact with other installed apps, you should be using \
            a `<queries>` declaration in your manifest. Using the `QUERY_ALL_PACKAGES` \
            permission in order to see all installed apps is rarely necessary, and most apps \
            on Google Play are not allowed to have this permission.

            See https://g.co/dev/packagevisibility for details.
            """.trimIndent()

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = EXPLANATION,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE,
            ),
            androidSpecific = true,
        ).addMoreInfo("https://g.co/dev/packagevisibility")
    }

    // -------------------------------------------------------------------------
    // XmlScanner — manifest <uses-permission> check
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (nameAttr.value != QUERY_ALL_PACKAGES) return

        val message =
            "QUERY_ALL_PACKAGES permission is not allowed for most apps on Google Play; " +
                "use a `<queries>` declaration instead."

        val incident = Incident(
            ISSUE,
            element,
            context.getValueLocation(nameAttr),
            message,
        )

        context.report(incident, targetSdkAtLeast(MIN_TARGET_SDK))
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — getInstalledPackages / getInstalledApplications calls
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> =
        listOf(GET_INSTALLED_PACKAGES, GET_INSTALLED_APPLICATIONS)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) return

        val message =
            "Calling `${method.name}` retrieves information about all installed packages. " +
                "If you only need to interact with specific apps, use a `<queries>` declaration " +
                "in your manifest rather than requesting QUERY_ALL_PACKAGES permission."

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )

        context.report(incident, targetSdkAtLeast(MIN_TARGET_SDK))
    }

    // -------------------------------------------------------------------------
    // filterIncident — suppress if targetSdk < 30 (package visibility rules
    // only apply from Android 11 / API 30 onwards)
    // -------------------------------------------------------------------------

    override fun filterIncident(context: JavaContext, incident: Incident, map: LintMap): Boolean {
        // When called from a Java/Kotlin source context the conditional
        // targetSdkAtLeast() guard already filters; return true to keep.
        return true
    }
}