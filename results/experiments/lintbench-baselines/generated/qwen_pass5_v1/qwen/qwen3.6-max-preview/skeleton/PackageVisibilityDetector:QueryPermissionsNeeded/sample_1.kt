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

class PackageVisibilityDetector : Detector(), Detector.SourceCodeScanner, Detector.XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        private const val QUERIES_TAG_PRESENT = "queriesTagPresent"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = "Apps targeting Android 11 (API 30) and higher are subject to package visibility restrictions. " +
                "Methods like PackageManager.getInstalledPackages and getInstalledApplications will only return a filtered list of apps " +
                "unless a <queries> element is declared in the manifest, or the app holds the QUERY_ALL_PACKAGES permission. " +
                "Consider using alternative APIs like queryIntentActivities or adding a <queries> declaration.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "manifest") {
            val queries = element.getElementsByTagName("queries")
            if (queries.length > 0) {
                context.project.putClientProperty(QUERIES_TAG_PRESENT, true)
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Apps targeting Android 11+ may need a `<queries>` declaration in the manifest to use this method, " +
                    "or should use alternative APIs like `queryIntentActivities`."
            )
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.project.targetSdkVersion < 30) return false
        if (context.project.getClientProperty(QUERIES_TAG_PRESENT) == true) return false
        return true
    }
}