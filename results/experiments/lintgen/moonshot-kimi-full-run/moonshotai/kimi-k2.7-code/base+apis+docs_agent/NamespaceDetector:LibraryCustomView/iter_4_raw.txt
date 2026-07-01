package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return XmlScannerConstants.ALL
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) {
            return
        }

        val tag = element.tagName
        if (tag != SdkConstants.VIEW_TAG && tag.indexOf('.') == -1) {
            return
        }

        if (tag == SdkConstants.VIEW_TAG) {
            val className = element.getAttribute(SdkConstants.ATTR_CLASS)
            if (className.isEmpty() || className.indexOf('.') == -1) {
                return
            }
        }

        val pkg = context.project.`package`
        if (pkg.isNullOrEmpty()) {
            return
        }

        val prefix = SdkConstants.NS_RESOURCES.substring(
            0,
            SdkConstants.NS_RESOURCES.lastIndexOf('/') + 1
        )
        val pkgUri = prefix + pkg

        var current: Element? = element
        while (current != null) {
            val attrs = current.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as? Attr ?: continue
                if (!attr.name.startsWith("xmlns")) {
                    continue
                }
                val value = attr.value
                if (value != pkgUri) {
                    continue
                }

                val fix = LintFix.create()
                    .replace()
                    .text(pkgUri)
                    .with(SdkConstants.AUTO_URI)
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "When using a custom view in a library, you should use the " +
                            "`${SdkConstants.AUTO_URI}` namespace instead of the library " +
                            "package namespace.",
                    fix
                )
            }
            current = current.parentNode as? Element
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto namespace",
            explanation = "When using a custom view with custom attributes in a library project, the " +
                    "layout must use the special `${SdkConstants.AUTO_URI}` namespace instead of a " +
                    "URI which includes the library project's own package. This will be used to " +
                    "automatically adjust the namespace of the attributes when the library " +
                    "resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}