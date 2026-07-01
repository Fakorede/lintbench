package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) return

        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    checkRestriction(context, element)
                } else {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Invalid element `<${element.tagName}>`; only `<$TAG_RESTRICTION>` is allowed inside `<$TAG_RESTRICTIONS>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        if (keyAttr == null || keyAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_KEY` on `<$TAG_RESTRICTION>`"
            )
            return
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (typeAttr == null || typeAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_RESTRICTION_TYPE` on `<$TAG_RESTRICTION>`"
            )
            return
        }

        val type = typeAttr.value
        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                typeAttr,
                context.getValueLocation(typeAttr),
                "Invalid `android:$ATTR_RESTRICTION_TYPE` value `$type`; must be one of: ${VALID_TYPES.joinToString()}"
            )
        }

        if (type == TYPE_CHOICE || type == TYPE_MULTI_SELECT) {
            var hasEntry = false
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName == TAG_ENTRY) {
                        hasEntry = true
                        val entryKey = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
                        val entryTitle = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
                        if (entryKey == null || entryKey.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement),
                                "Missing required attribute `android:$ATTR_KEY` on `<$TAG_ENTRY>`"
                            )
                        }
                        if (entryTitle == null || entryTitle.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement),
                                "Missing required attribute `android:$ATTR_TITLE` on `<$TAG_ENTRY>`"
                            )
                        }
                    } else {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(childElement),
                            "Invalid element `<${childElement.tagName}>`; only `<$TAG_ENTRY>` is allowed inside `<$TAG_RESTRICTION>` of type `$type`"
                        )
                    }
                }
                child = child.nextSibling
            }
            if (!hasEntry) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction of type `$type` must contain at least one `<$TAG_ENTRY>` element"
                )
            }
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_ENTRY = "entry"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"

        private val VALID_TYPES = setOf("bool", TYPE_CHOICE, TYPE_MULTI_SELECT, "string", "integer", "bundle", "bundle_array")

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed according to the RestrictionsManager API.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}