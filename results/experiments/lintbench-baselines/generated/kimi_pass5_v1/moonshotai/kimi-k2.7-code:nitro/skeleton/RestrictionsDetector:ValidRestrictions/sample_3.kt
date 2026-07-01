package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val FILE_NAME = "app_restrictions.xml"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private val VALID_TYPES = setOf(
            "bool",
            "string",
            "integer",
            "choice",
            "multi_select",
            "hidden",
            "bundle",
            "bundle_array"
        )

        private val TYPES_REQUIRING_ENTRIES = setOf("choice", "multi_select")

        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                The application restrictions descriptor (res/xml/app_restrictions.xml) must be
                properly formed. The root element must be <restrictions>, and each top-level
                child must be a <restriction> with a non-empty android:key, android:title,
                and android:restrictionType attribute. The restrictionType must be one of the
                supported values: bool, string, integer, choice, multi_select, hidden, bundle,
                or bundle_array. Types "choice" and "multi_select" also require android:entries
                and android:entryValues. Bundle types may contain nested <restriction> elements.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.file.name != FILE_NAME) {
            return
        }

        val root = document.documentElement ?: return
        val rootLocation = context.getElementLocation(root)

        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE,
                rootLocation,
                "Restrictions descriptor file must have a root <$TAG_RESTRICTIONS> element"
            )
            return
        }

        for (child in root.childElements) {
            if (child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    context.getElementLocation(child),
                    "Top-level element inside <$TAG_RESTRICTIONS> must be <$TAG_RESTRICTION>, " +
                            "not <${child.tagName}>"
                )
            } else {
                checkRestriction(context, child)
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        if (element.tagName != TAG_RESTRICTION) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "Expected <$TAG_RESTRICTION> element, found <${element.tagName}>"
            )
            return
        }

        val location = context.getElementLocation(element)
        val key = getAttribute(element, ATTR_KEY)
        val title = getAttribute(element, ATTR_TITLE)
        val type = getAttribute(element, ATTR_TYPE)

        if (key.isNullOrBlank()) {
            context.report(ISSUE, location, "<$TAG_RESTRICTION> must specify a non-empty android:$ATTR_KEY")
        }
        if (title.isNullOrBlank()) {
            context.report(ISSUE, location, "<$TAG_RESTRICTION> must specify a non-empty android:$ATTR_TITLE")
        }
        if (type.isNullOrBlank()) {
            context.report(ISSUE, location, "<$TAG_RESTRICTION> must specify android:$ATTR_TYPE")
            return
        }

        if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                location,
                "Invalid restriction type \"$type\"; must be one of: ${VALID_TYPES.joinToString()}"
            )
            return
        }

        if (type in TYPES_REQUIRING_ENTRIES) {
            if (getAttribute(element, ATTR_ENTRIES).isNullOrBlank()) {
                context.report(
                    ISSUE,
                    location,
                    "Restriction type \"$type\" requires android:$ATTR_ENTRIES"
                )
            }
            if (getAttribute(element, ATTR_ENTRY_VALUES).isNullOrBlank()) {
                context.report(
                    ISSUE,
                    location,
                    "Restriction type \"$type\" requires android:$ATTR_ENTRY_VALUES"
                )
            }
        }

        if (type == "bundle" || type == "bundle_array") {
            for (child in element.childElements) {
                if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        context.getElementLocation(child),
                        "Nested element inside bundle restriction must be <$TAG_RESTRICTION>, " +
                                "not <${child.tagName}>"
                    )
                } else {
                    if (type == "bundle_array") {
                        val childType = getAttribute(child, ATTR_TYPE)
                        if (childType != null && childType != "bundle") {
                            context.report(
                                ISSUE,
                                context.getElementLocation(child),
                                "Children of a bundle_array restriction must have type \"bundle\""
                            )
                        }
                    }
                    checkRestriction(context, child)
                }
            }
        }
    }

    private fun getAttribute(element: Element, name: String): String? {
        val namespaced = element.getAttributeNS(ANDROID_URI, name)
        if (namespaced.isNotBlank()) {
            return namespaced
        }
        val local = element.getAttribute(name)
        return if (local.isNotBlank()) local else null
    }

    private val Node.childElements: List<Element>
        get() {
            if (this !is Element) return emptyList()
            val nodes = childNodes ?: return emptyList()
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }
}