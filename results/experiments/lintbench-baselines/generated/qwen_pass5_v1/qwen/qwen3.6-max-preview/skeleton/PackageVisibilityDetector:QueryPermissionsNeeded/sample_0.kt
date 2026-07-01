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

        private val AFFECTED_METHODS = listOf("getInstalledPackages", "getInstalledApplications")

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = "Apps that target Android 11 (API level 30) and higher cannot query or interact with " +
                "other installed apps by default. Calls to getInstalledPackages or getInstalledApplications " +
                "will return a filtered list. To query specific apps, add a <queries> element to your " +
                "manifest or use alternative APIs like getPackageInfo or queryIntentActivities. " +
                "See https://g.co/dev/packagevisibility for more information.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val modulesWithQueries = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String>? = listOf("queries", "uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "queries" -> modulesWithQueries.add(getModuleKey(context))
            "uses-permission" -> {
                if (element.getAttribute("android:name") == "android.permission.QUERY_ALL_PACKAGES") {
                    modulesWithQueries.add(getModuleKey(context))
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? = AFFECTED_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            val incident = Incident(context)
                .issue(ISSUE)
                .at(node)
                .message("`${method.name}` is affected by package visibility changes in Android 11+. " +
                    "Consider adding a `<queries>` declaration to your manifest or using " +
                    "`queryIntentActivities`/`getPackageInfo`.")
            incident.map().put("moduleKey", getModuleKey(context))
            context.report(incident)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val moduleKey = map.getString("moduleKey")
        // Suppress warning if the module already declares <queries> or QUERY_ALL_PACKAGES
        return moduleKey == null || !modulesWithQueries.contains(moduleKey)
    }

    private fun getModuleKey(context: Context): String {
        return context.module?.dir?.path ?: context.project.dir.path
    }
}