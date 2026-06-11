package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            "PackageVisibility",
            "Apps that target Android 11 cannot query or interact with other installed apps by default.",
            "If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest. " +
                    "The methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer return information about all installed apps.",
            "To query specific apps or types of apps, use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.",
            Category.CORRECTNESS,
            6,
            Implementation(PackageVisibilityDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            context.report(ISSUE, node, context.getLocation(node), "Apps targeting Android 11 cannot use this method to query all installed packages.")
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "manifest") {
            val queries = element.getElementsByTagName("queries").item(0)
            if (queries == null && context.evaluator.isTargetSdkAtLeast(30)) {
                context.report(ISSUE, element, context.getLocation(element), "Apps targeting Android 11 should declare a <queries> tag in the manifest.")
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MANIFEST
    }
}