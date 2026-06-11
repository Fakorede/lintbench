package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "PackageVisibility",
            briefDescription = "Using APIs affected by package visibility restrictions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps by default. If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, ANDROID_CONTENT_PM_PACKAGE_MANAGER)) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Using APIs affected by package visibility restrictions"
            )
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_MANIFEST)
    }

    @VisibleForTesting
    fun isManifestTargetingAndroid11OrHigher(context: XmlContext): Boolean {
        val manifest = context.xmlDocument.documentElement
        val usesSdkNode = manifest.getElementsByTagName(TAG_USES_SDK).item(0) as Element?
        if (usesSdkNode != null) {
            val targetSdkVersionAttr = usesSdkNode.getAttribute(ATTR_TARGET_SDK_VERSION)
            return targetSdkVersionAttr.toIntOrNull() ?: 0 >= 30
        }
        return false
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == TAG_MANIFEST) {
            val queries = element.getElementsByTagName(TAG_QUERIES).item(0)
            if (queries == null && isManifestTargetingAndroid11OrHigher(context)) {
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