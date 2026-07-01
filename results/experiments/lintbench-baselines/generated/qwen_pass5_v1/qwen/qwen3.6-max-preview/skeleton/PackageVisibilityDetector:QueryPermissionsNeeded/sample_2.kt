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

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = "Apps that target Android 11 (API level 30) or higher cannot query or interact with " +
                "other installed apps by default. PackageManager#getInstalledPackages and " +
                "PackageManager#getInstalledApplications will no longer return information about all installed apps. " +
                "To query specific apps or types of apps, you may need to add a <queries> declaration in your " +
                "manifest or use alternative methods like PackageManager#queryIntentActivities.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        // XML scanning is included to satisfy the XmlScanner contract.
        // No manifest-specific validation is required for this issue.
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using `${method.name}` may not return all installed apps on Android 11+ due to package visibility " +
                "restrictions. Consider adding a `<queries>` element to your manifest or using alternative APIs " +
                "like `queryIntentActivities`."
        )
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project.targetSdkVersion ?: return true
        return targetSdk >= 30
    }
}