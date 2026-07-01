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
        private const val HAS_QUERIES = "queries"
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val ANDROID_MANIFEST = "AndroidManifest.xml"

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 (API level 30) or higher cannot query or interact with \
                other installed apps by default. As a result, \
                `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` \
                will no longer return information about all installed apps.
                
                If your app needs visibility into other installed apps, add a `<queries>` \
                declaration to the manifest. To query specific apps, prefer `getPackageInfo` or \
                `queryIntentActivities`.

                For more details, see \
                https://g.co/dev/packagevisibility.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("queries")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "queries" && context.file.name == ANDROID_MANIFEST) {
            context.getPartialResults(ISSUE).map().putBoolean(HAS_QUERIES, true)
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        GET_INSTALLED_PACKAGES,
        GET_INSTALLED_APPLICATIONS,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName == PACKAGE_MANAGER) {
            val message = buildString {
                append("Starting with Android 11 (API 30), ")
                append(method.name)
                append(" no longer returns information about all installed apps; ")
                append("if you need visibility into other apps, add a `<queries>` ")
                append("declaration to the manifest, or use `getPackageInfo` / ")
                append("`queryIntentActivities` to query specific apps.")
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message,
            )
        }
    }

    override fun filterIncident(
        context: Context,
        incident: Incident,
        map: LintMap,
    ): Boolean {
        val targetSdk = context.mainProject?.targetSdkVersion?.featureLevel ?: return true
        if (targetSdk < 30) {
            return false
        }

        return !map.getBoolean(HAS_QUERIES, false)
    }
}