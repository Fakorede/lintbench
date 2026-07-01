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
        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"
        private const val USES_SDK = "uses-sdk"
        private const val ATTR_TARGET_SDK_VERSION = "android:targetSdkVersion"
        private const val KEY_TARGET_SDK_30 = "targetSdk30"

        private val EXPLANATION = """
            Apps that target Android 11 (API level 30) or higher cannot query or interact with
            other installed apps by default. If your app needs to query or interact with other
            installed apps, add a `<queries>` element to your `AndroidManifest.xml`.

            As a side effect, `PackageManager#getInstalledPackages` and
            `PackageManager#getInstalledApplications` will no longer return information about
            all installed apps. To query specific apps or types of apps, use methods such as
            `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities` instead.
        """.trimIndent()

        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf(GET_INSTALLED_PACKAGES, GET_INSTALLED_APPLICATIONS)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != PACKAGE_MANAGER) {
            return
        }

        val message = "Use of `PackageManager#${method.name}()` is affected by package " +
            "visibility restrictions on Android 11 (API level 30) and higher. To query " +
            "specific apps, use `PackageManager#getPackageInfo()` or " +
            "`PackageManager#queryIntentActivities()`, or add a `<queries>` declaration to " +
            "your manifest."

        val incident = Incident(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = message,
        )
        context.report(incident)
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(USES_SDK)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != USES_SDK) {
            return
        }

        val targetSdk = element.getAttribute(ATTR_TARGET_SDK_VERSION)
        val level = targetSdk.toIntOrNull() ?: return
        if (level >= 30) {
            context.getPartialResults(ISSUE).putBoolean(KEY_TARGET_SDK_30, true)
        }
    }

    override fun filterIncident(
        context: Context,
        incident: Incident,
        map: LintMap,
    ): Boolean {
        if (map.getBoolean(KEY_TARGET_SDK_30, false)) {
            return true
        }
        return context.project.targetSdkVersion.isAtLeast(30)
    }
}