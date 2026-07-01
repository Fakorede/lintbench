package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_KEY
import com.android.SdkConstants.ATTR_TITLE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlFile
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(context: XmlContext, file: XmlFile): Boolean {
        val root = file.document.documentElement ?: return false
        return root.tagName == TAG_RESTRICTIONS
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE, root, context.getElementLocation(root),
                "The root element must be <$TAG_RESTRICTIONS>"
            )
            return
        }

        root.forEachChildTag { child ->
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE, child, context.getElementLocation(child),
                    "Expected <$TAG_RESTRICTION> but was <${child.tagName}>"
                )
            } else {
                checkRestriction(context, child)
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        val title = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)

        if (key == null) {
            context.report(ISSUE, element, context.getElementLocation(element),
                "Missing required attribute android:key")
        }
        if (title == null) {
            context.report(ISSUE, element, context.getElementLocation(element),
                "Missing required attribute android:title")
        }
        if (typeAttr == null) {
            context.report(ISSUE, element, context.getElementLocation(element),
                "Missing required attribute android:restrictionType")
            return
        }

        val type = typeAttr.value
        if (type !in VALID_TYPES) {
            context.report(ISSUE, typeAttr, context.getValueLocation(typeAttr),
                "Invalid restriction type: $type. Must be one of: ${VALID_TYPES.joinToString()}")
            return
        }

        element.forEachChildTag { child ->
            when (type) {
                TYPE_CHOICE, TYPE_MULTI_SELECT -> {
                    if (child.tagName != TAG_ENTRY) {
                        context.report(ISSUE, child, context.getElementLocation(child),
                            "Expected <$TAG_ENTRY> for type $type but was <${child.tagName}>")
                    } else {
                        checkEntry(context, child)
                    }
                }
                TYPE_BUNDLE -> {
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(ISSUE, child, context.getElementLocation(child),
                            "Expected <$TAG_RESTRICTION> for type $type but was <${child.tagName}>")
                    } else {
                        checkRestriction(context, child)
                    }
                }
                TYPE_BUNDLE_ARRAY -> {
                    if (child.tagName != TAG_BUNDLE) {
                        context.report(ISSUE, child, context.getElementLocation(child),
                            "Expected <$TAG_BUNDLE> for type $type but was <${child.tagName}>")
                    } else {
                        checkBundle(context, child)
                    }
                }
                else -> {
                    context.report(ISSUE, child, context.getElementLocation(child),
                        "Element <$TAG_RESTRICTION> with type $type should not have child elements")
                }
            }
        }
    }

    private fun checkEntry(context: XmlContext, element: Element) {
        if (element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY) == null) {
            context.report(ISSUE, element, context.getElementLocation(element),
                "Missing required attribute android:key on <$TAG_ENTRY>")
        }
        if (element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE) == null) {
            context.report(ISSUE, element, context.getElementLocation(element),
                "Missing required attribute android:title on <$TAG_ENTRY>")
        }
    }

    private fun checkBundle(context: XmlContext, element: Element) {
        element.forEachChildTag { child ->
            if (child.tagName != TAG_RESTRICTION) {
                context.report(ISSUE, child, context.getElementLocation(child),
                    "Expected <$TAG_RESTRICTION> inside <$TAG_BUNDLE> but was <${child.tagName}>")
            } else {
                checkRestriction(context, child)
            }
        }
    }

    private fun Element.forEachChildTag(block: (Element) -> Unit) {
        var child = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                block(child as Element)
            }
            child = child.nextSibling
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_ENTRY = "entry"
        private const val TAG_BUNDLE = "bundle"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_TYPES = setOf(
            "bool", "string", "integer", TYPE_CHOICE, TYPE_MULTI_SELECT,
            "hidden", TYPE_BUNDLE, TYPE_BUNDLE_ARRAY
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}