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

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps 
                by default. If you need to query or interact with other installed apps, you may need 
                to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and 
                `PackageManager#getInstalledApplications` will no longer return information about all 
                installed apps. To query specific apps or types of apps, you can use methods like 
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

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        // No-op: We perform checking on the fully merged manifest in filterIncident
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Consider avoiding `PackageManager.${method.name}` when targeting Android 11 or higher " +
            "as this method will no longer return information about all installed apps."
        )
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val project = context.mainProject
        if (project.targetSdkVersion.apiLevel < 30) {
            return false
        }

        val document = project.mergedManifest ?: return true
        val root = document.documentElement ?: return true

        // Check if QUERY_ALL_PACKAGES is declared
        val permissions = root.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val item = permissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { item.getAttribute("android:name") }
            if (name == "android.permission.QUERY_ALL_PACKAGES") {
                return false
            }
        }

        // Check if <queries> element is present
        val queries = root.getElementsByTagName("queries")
        if (queries.length > 0) {
            return false
        }

        return true
    }
}