package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            "PackageVisibility",
            "Using APIs affected by package visibility restrictions",
            """
                Apps that target Android 11 cannot query or interact with other installed apps by default. If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest.
            """.trimIndent(),
            "https://g.co/dev/packagevisibility",
            Category.CORRECTNESS,
            6,
            Implementation(PackageVisibilityDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, ANDROID_CONTENT_PM_PACKAGE_MANAGER)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using APIs affected by package visibility restrictions"
            )
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_MANIFEST)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == TAG_MANIFEST) {
            val queries = element.getElementsByTagName(TAG_QUERIES).item(0)
            if (queries == null && context.apiLevel >= 30) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Targeting Android 11 and above requires a <queries> declaration in your manifest"
                )
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MANIFEST
    }
}