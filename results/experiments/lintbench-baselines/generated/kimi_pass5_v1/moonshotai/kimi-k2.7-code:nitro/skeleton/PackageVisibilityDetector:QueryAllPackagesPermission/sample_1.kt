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
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If your app needs to query or interact with other installed apps, you \
                should use a `<queries>` declaration in your `AndroidManifest.xml` \
                instead of the `QUERY_ALL_PACKAGES` permission. Declaring \
                `QUERY_ALL_PACKAGES` lets your app see all other apps installed on the \
                device, which is rarely necessary and is not allowed for most apps \
                distributed on Google Play. For more information, see \
                https://g.co/dev/packagevisibility.
            """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:name")
        if (name == QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Declaring QUERY_ALL_PACKAGES is not allowed for most apps; " +
                    "use a `<queries>` declaration in your manifest instead.",
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf(GET_INSTALLED_PACKAGES, GET_INSTALLED_APPLICATIONS)

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != PACKAGE_MANAGER) return

        val message = when (method.name) {
            GET_INSTALLED_PACKAGES ->
                "getInstalledPackages() returns a list of all installed packages; "
            GET_INSTALLED_APPLICATIONS ->
                "getInstalledApplications() returns a list of all installed apps; "
            else -> return
        } +
            "consider using a `<queries>` declaration in your manifest " +
            "instead of the QUERY_ALL_PACKAGES permission."

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
    }

    override fun filterIncident(
        context: Context, incident: Incident, map: LintMap,
    ): Boolean = true
}