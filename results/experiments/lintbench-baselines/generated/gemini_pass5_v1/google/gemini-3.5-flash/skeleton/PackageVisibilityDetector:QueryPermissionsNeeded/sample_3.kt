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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasQueryAllPackages = false

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

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
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            .ifEmpty { element.getAttribute("android:name") }
        if (name == "android.permission.QUERY_ALL_PACKAGES") {
            hasQueryAllPackages = true
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
            val incident = Incident(context, ISSUE)
                .at(node)
                .message("Queries permissions are needed to call `${method.name}` when targeting Android 11 or higher")
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val mainProject = context.mainProject
        if (mainProject.targetSdkVersion.featureLevel < 30) {
            return false
        }
        if (hasQueryAllPackages) {
            return false
        }
        val mergedManifest = mainProject.mergedManifest
        if (mergedManifest != null) {
            val permissions = mergedManifest.getElementsByTagName("uses-permission")
            for (i in 0 until permissions.length) {
                val item = permissions.item(i) as? Element ?: continue
                val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                    .ifEmpty { item.getAttribute("android:name") }
                if (name == "android.permission.QUERY_ALL_PACKAGES") {
                    return false
                }
            }
        }
        return true
    }
}