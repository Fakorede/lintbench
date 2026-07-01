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

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableElements(): kotlin.collections.Collection<String> {
        return kotlin.collections.listOf("queries")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val message = "The `<queries>` element is only needed when targeting Android 11 (API 30) or higher."
        context.report(
            Incident(
                QUERY_PERMISSIONS_NEEDED,
                element,
                context.getNameLocation(element),
                message
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            val message = "PackageManager#${method.name} will no longer return information about all installed apps when targeting Android 11 or higher. To query specific apps or types of apps, use methods like PackageManager#getPackageInfo or PackageManager#queryIntentActivities."
            context.report(
                Incident(
                    QUERY_PERMISSIONS_NEEDED,
                    node,
                    context.getLocation(node),
                    message
                )
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        val isXml = incident.file.name.endsWith(".xml")
        return if (isXml) {
            targetSdk < 30
        } else {
            targetSdk >= 30
        }
    }

    companion object {
        @JvmField
        val QUERY_PERMISSIONS_NEEDED = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer return information about all installed apps. To query specific apps or types of apps, you can use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
        )
    }
}