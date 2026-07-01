package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
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

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed. " +
                "The root element must be <restrictions>, and it must contain <restriction> elements " +
                "with the required attributes: android:key, android:title, and android:restrictionType. " +
                "Valid restriction types are: bool, choice, multi-select, integer, string, bundle, and bundle_array.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "integer", "string", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE, root, context.getLocation(root),
                "Root element must be <$TAG_RESTRICTIONS>"
            )
            return
        }
        checkRestrictions(context, root)
    }

    private fun checkRestrictions(context: XmlContext, parent: Element) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE, element, context.getLocation(element),
                        "Expected <$TAG_RESTRICTION> but found <${element.tagName}>"
                    )
                } else {
                    checkRestriction(context, element)
                    // Bundles can contain nested restrictions
                    checkRestrictions(context, element)
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        if (!element.hasAttributeNS(ANDROID_NS, ATTR_KEY)) {
            context.report(
                ISSUE, element, context.getLocation(element),
                "Missing required attribute android:$ATTR_KEY"
            )
        }
        if (!element.hasAttributeNS(ANDROID_NS, ATTR_TITLE)) {
            context.report(
                ISSUE, element, context.getLocation(element),
                "Missing required attribute android:$ATTR_TITLE"
            )
        }
        val type = element.getAttributeNS(ANDROID_NS, ATTR_RESTRICTION_TYPE)
        if (type.isEmpty()) {
            context.report(
                ISSUE, element, context.getLocation(element),
                "Missing required attribute android:$ATTR_RESTRICTION_TYPE"
            )
        } else if (!VALID_TYPES.contains(type)) {
            context.report(
                ISSUE, element, context.getLocation(element),
                "Invalid restriction type: $type. Must be one of: ${VALID_TYPES.joinToString()}"
            )
        }
    }
}