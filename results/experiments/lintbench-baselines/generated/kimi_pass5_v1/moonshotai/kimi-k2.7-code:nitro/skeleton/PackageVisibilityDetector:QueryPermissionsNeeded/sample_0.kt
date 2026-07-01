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

    companion object {
        private const val HAS_QUERY_ELEMENT = "has_query_element"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 (API level 30) or higher cannot query or
                interact with other installed apps by default. If your app needs to do
                so, you should add a `<queries>` declaration to the `AndroidManifest.xml`.

                Because of this, `PackageManager#getInstalledPackages` and
                `PackageManager#getInstalledApplications` no longer return information
                about all installed apps. To query specific apps or types of apps,
                consider using methods such as `PackageManager#getPackageInfo` or
                `PackageManager#queryIntentActivities`.

                For more details, see https://g.co/dev/packagevisibility.
            """,
            moreInfo = "https://g.co/dev/packagevisibility",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("queries")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "queries") {
            context.map.putBoolean(HAS_QUERY_ELEMENT, true)
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
        if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        val message = "Using `PackageManager#getInstalledPackages` or " +
            "`PackageManager#getInstalledApplications` is affected by query permissions " +
            "on Android 11+. You may need to add a `<queries>` declaration to your " +
            "manifest, or use `getPackageInfo` / `queryIntentActivities` instead."

        context.report(ISSUE, node, context.getLocation(node), message)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return !map.getBoolean(HAS_QUERY_ELEMENT, false)
    }
}