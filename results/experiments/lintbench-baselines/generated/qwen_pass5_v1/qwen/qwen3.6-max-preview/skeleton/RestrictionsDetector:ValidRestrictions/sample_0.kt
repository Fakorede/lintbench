package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private val VALID_RESTRICTION_TYPES = setOf(
            "bool", "choice", "multi-select", "string", "integer", "hidden", "bundle", "bundle_array"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed according to the RestrictionsManager schema.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }
        checkChildren(context, root)
    }

    private fun checkChildren(context: XmlContext, parent: Element) {
        var child: Node? = parent.firstChild
        while (child != null) {
            if (child is Element) {
                if (child.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        context.getLocation(child),
                        "Only <restriction> elements are allowed inside <restrictions>"
                    )
                } else {
                    checkRestriction(context, child)
                    checkChildren(context, child)
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_URI, "key")
        val title = element.getAttributeNS(ANDROID_URI, "title")
        val type = element.getAttributeNS(ANDROID_URI, "restrictionType")

        if (key.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute android:key"
            )
        }
        if (title.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute android:title"
            )
        }
        if (type.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing required attribute android:restrictionType"
            )
        } else if (type !in VALID_RESTRICTION_TYPES) {
            context.report(
                ISSUE,
                context.getLocation(element, "android:restrictionType"),
                "Invalid android:restrictionType: must be one of ${VALID_RESTRICTION_TYPES.joinToString(", ")}"
            )
        }
    }
}