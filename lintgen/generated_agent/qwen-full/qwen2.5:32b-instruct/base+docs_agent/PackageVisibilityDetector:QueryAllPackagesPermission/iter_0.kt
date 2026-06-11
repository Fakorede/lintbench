package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.Density
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, ManifestFilter {

    companion object {
        val ISSUE = Issue.create(
            id = "PackageVisibility",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                Using the `QUERY_ALL_PACKAGES` permission in order to see all installed apps is rarely necessary. 
                Most apps on Google Play are not allowed to have this permission. If you need to query or interact with other installed apps, you should be using a `<queries>` declaration in your manifest.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            androidSpecific = true,
            references = arrayOf("https://g.co/dev/packagevisibility")
        )
    }

    override fun appliesToAttributes(): Boolean {
        return false
    }

    override fun visitManifest(context: JavaContext, manifest: Element): Boolean {
        val permissions = context.getManifest().permissions
        for (permission in permissions) {
            if (permission.name == "android.permission.QUERY_ALL_PACKAGES") {
                context.report(
                    ISSUE,
                    context.getLocation(permission.element),
                    "Using the QUERY_ALL_PACKAGES permission is discouraged. Consider using a <queries> declaration instead."
                )
            }
        }
        return super.visitManifest(context, manifest)
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_MANIFEST_PERMISSION)
    }
}