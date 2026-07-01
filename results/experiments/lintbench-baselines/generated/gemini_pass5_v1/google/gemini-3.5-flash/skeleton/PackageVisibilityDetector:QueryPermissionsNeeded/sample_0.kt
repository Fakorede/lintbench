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
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        private const val KEY_HAS_QUERIES = "PackageVisibilityDetector.hasQueries"
        private const val KEY_HAS_QUERY_ALL = "PackageVisibilityDetector.hasQueryAll"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. \
                If you need to query or interact with other installed apps, you may need to add a `<queries>` \
                declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("queries", "uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "queries") {
            context.project.putProperty(KEY_HAS_QUERIES, true)
        } else if (tagName == "uses-permission") {
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.permission.QUERY_ALL_PACKAGES") {
                context.project.putProperty(KEY_HAS_QUERY_ALL, true)
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
            val incident = Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Querying some packages might require a `<queries>` declaration in your manifest"
            )
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.project.targetSdkVersion.featureLevel < 30) {
            return false
        }
        if (context.project.getProperty(KEY_HAS_QUERY_ALL) == true) {
            return false
        }
        return true
    }
}