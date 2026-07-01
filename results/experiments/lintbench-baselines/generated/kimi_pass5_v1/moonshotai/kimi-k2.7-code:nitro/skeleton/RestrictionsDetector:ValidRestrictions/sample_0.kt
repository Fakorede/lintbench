package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val APP_RESTRICTIONS_FILE = "app_restrictions.xml"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val TAG_ENTRY = "entry"
        private const val TAG_DEFAULT_ENTRY = "defaultEntry"

        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_VALUE = "value"

        private val VALID_TYPES = setOf(
            "bool",
            "string",
            "integer",
            "choice",
            "multi-select",
            "bundle",
            "hidden"
        )

        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                The restrictions descriptor XML file (res/xml/app_restrictions.xml) must
                have a `<restrictions>` root element containing only `<restriction>`
                children. Each `<restriction>` must specify `android:key`,
                `android:title`, and `android:restrictionType`, and the type must be one
                of the supported values.

                `bundle`-type restrictions must contain nested `<restriction>` children.
                `choice` and `multi-select` restrictions must contain `<entry>` children
                with `android:key` and `android:value`, and may also contain a
                `<defaultEntry>` for choice types.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        if (!context.file.name.equals(APP_RESTRICTIONS_FILE, ignoreCase = true)) {
            return
        }

        val root = document.documentElement ?: return

        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE,
                context.getElementLocation(root),
                "Restrictions files must have a <restrictions> root element"
            )
            return
        }

        if (root.getAttribute("xmlns:android") != ANDROID_NS) {
            context.report(
                ISSUE,
                context.getElementLocation(root),
                "The <restrictions> element must declare xmlns:android=\"$ANDROID_NS\""
            )
        }

        val seenKeys = mutableSetOf<String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            val childElement = child as? org.w3c.dom.Element ?: continue
            if (childElement.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    context.getElementLocation(childElement),
                    "The <restrictions> element may only contain <restriction> children"
                )
            } else {
                checkRestriction(context, childElement, seenKeys)
            }
        }
    }

    private fun checkRestriction(
        context: XmlContext,
        element: org.w3c.dom.Element,
        seenKeys: MutableSet<String>
    ) {
        val key = getAndroidAttribute(element, ATTR_KEY)
        if (key.isNullOrBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "A <restriction> must specify an android:key attribute"
            )
        } else if (!seenKeys.add(key)) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "Duplicate restriction key \"$key\""
            )
        }

        val title = getAndroidAttribute(element, ATTR_TITLE)
        if (title.isNullOrBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "A <restriction> must specify an android:title attribute"
            )
        }

        val restrictionType = getAndroidAttribute(element, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isNullOrBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "A <restriction> must specify an android:restrictionType attribute"
            )
            return
        }

        if (restrictionType !in VALID_TYPES) {
            context.report(
                ISSUE,
                getAndroidAttributeLocation(context, element, ATTR_RESTRICTION_TYPE),
                "Invalid restriction type \"$restrictionType\"; must be one of: ${
                    VALID_TYPES.joinToString()
                }"
            )
            return
        }

        when (restrictionType) {
            "bundle" -> checkBundleChildren(context, element, seenKeys)
            "choice" -> checkChoiceChildren(context, element, true)
            "multi-select" -> checkChoiceChildren(context, element, false)
            else -> checkNoElementChildren(context, element, restrictionType)
        }
    }

    private fun checkBundleChildren(
        context: XmlContext,
        element: org.w3c.dom.Element,
        seenKeys: MutableSet<String>
    ) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            val childElement = child as? org.w3c.dom.Element ?: continue
            if (childElement.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    context.getElementLocation(childElement),
                    "A bundle-typed restriction must contain only <restriction> children"
                )
            } else {
                checkRestriction(context, childElement, seenKeys)
            }
        }
    }

    private fun checkChoiceChildren(
        context: XmlContext,
        element: org.w3c.dom.Element,
        allowDefaultEntry: Boolean
    ) {
        var hasEntry = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            val childElement = child as? org.w3c.dom.Element ?: continue
            when (childElement.tagName) {
                TAG_ENTRY -> {
                    hasEntry = true
                    val entryKey = getAndroidAttribute(childElement, ATTR_KEY)
                    if (entryKey.isNullOrBlank()) {
                        context.report(
                            ISSUE,
                            context.getElementLocation(childElement),
                            "An <entry> must specify an android:key attribute"
                        )
                    }
                    val entryValue = getAndroidAttribute(childElement, ATTR_VALUE)
                    if (entryValue.isNullOrBlank()) {
                        context.report(
                            ISSUE,
                            context.getElementLocation(childElement),
                            "An <entry> must specify an android:value attribute"
                        )
                    }
                }
                TAG_DEFAULT_ENTRY -> {
                    if (!allowDefaultEntry) {
                        context.report(
                            ISSUE,
                            context.getElementLocation(childElement),
                            "A <defaultEntry> is not allowed inside a multi-select restriction"
                        )
                    }
                    val defaultValue = getAndroidAttribute(childElement, ATTR_VALUE)
                    if (defaultValue.isNullOrBlank()) {
                        context.report(
                            ISSUE,
                            context.getElementLocation(childElement),
                            "A <defaultEntry> must specify an android:value attribute"
                        )
                    }
                }
                else -> {
                    context.report(
                        ISSUE,
                        context.getElementLocation(childElement),
                        "A choice/multi-select restriction must contain only <entry> " +
                                "and <defaultEntry> children"
                    )
                }
            }
        }
        if (!hasEntry) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "A choice/multi-select restriction must contain at least one <entry>"
            )
        }
    }

    private fun checkNoElementChildren(
        context: XmlContext,
        element: org.w3c.dom.Element,
        restrictionType: String
    ) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            if (child is org.w3c.dom.Element) {
                context.report(
                    ISSUE,
                    context.getElementLocation(child),
                    "A $restrictionType-typed restriction should not contain child elements"
                )
            }
        }
    }

    private fun getAndroidAttribute(element: org.w3c.dom.Element, name: String): String? {
        val value = element.getAttributeNS(ANDROID_NS, name)
        if (value.isNotBlank()) return value

        val prefixed = element.getAttribute("android:$name")
        return if (prefixed.isNotBlank()) prefixed else null
    }

    private fun getAndroidAttributeLocation(
        context: XmlContext,
        element: org.w3c.dom.Element,
        name: String
    ): Location {
        val attr = element.getAttributeNodeNS(ANDROID_NS, name)
            ?: element.getAttributeNode("android:$name")
        return if (attr != null) {
            context.getLocation(attr)
        } else {
            context.getElementLocation(element)
        }
    }
}