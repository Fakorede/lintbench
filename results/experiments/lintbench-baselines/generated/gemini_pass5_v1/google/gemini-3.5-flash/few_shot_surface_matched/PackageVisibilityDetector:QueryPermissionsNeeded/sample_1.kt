package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val queryAllPackagesProjects = java.util.HashSet<Project>()

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "uses-permission") {
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.permission.QUERY_ALL_PACKAGES") {
                queryAllPackagesProjects.add(context.project)
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            return
        }

        val location = context.getNameLocation(node)
        val message = "PackageManager.${method.name} is affected by package visibility rules on Android 11 and above. " +
                "To query specific apps, use more specific APIs like queryIntentActivities, or add a <queries> declaration."

        val incident = Incident(ISSUE, node, location, message)
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        val project = incident.project
        if (project.targetSdk < 30) {
            return false
        }
        if (queryAllPackagesProjects.contains(project)) {
            return false
        }
        return true
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps \
                by default. If you need to query or interact with other installed apps, you may need \
                to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}