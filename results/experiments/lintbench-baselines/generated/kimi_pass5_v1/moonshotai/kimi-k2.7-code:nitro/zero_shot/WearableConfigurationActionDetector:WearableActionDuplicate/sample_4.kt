package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_MANIFEST)

    override fun visitDocument(context: XmlContext) {
        val root = context.document.documentElement ?: return
        if (root.tagName != TAG_MANIFEST) return

        val minSdk = context.project.minSdk
        val requiredActions = mutableSetOf<String>()
        val actionToMetaElements = mutableMapOf<String, MutableList<Element>>()

        for (service in root.descendants(TAG_SERVICE)) {
            for (metaData in service.descendants(TAG_META_DATA)) {
                val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotBlank() } ?: continue
                val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE).takeIf { it.isNotBlank() } ?: continue

                if (name == WEARABLE_CONFIGURATION_ACTION_NAME || name == SHORT_CONFIGURATION_ACTION_NAME) {
                    requiredActions.add(value)
                    actionToMetaElements.getOrPut(value) { mutableListOf() }.add(metaData)
                }
            }
        }

        if (requiredActions.isEmpty()) return

        val actionToActivities = mutableMapOf<String, MutableList<Element>>()

        for (activity in root.descendants(TAG_ACTIVITY)) {
            for (intentFilter in activity.descendants(TAG_INTENT_FILTER)) {
                val actions = mutableSetOf<String>()
                val categories = mutableSetOf<String>()

                for (child in intentFilter.childNodes) {
                    if (child !is Element) continue
                    when (child.tagName) {
                        TAG_ACTION -> child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            .takeIf { it.isNotBlank() }
                            ?.let { actions.add(it) }
                        TAG_CATEGORY -> child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            .takeIf { it.isNotBlank() }
                            ?.let { categories.add(it) }
                    }
                }

                for (action in actions) {
                    if (minSdk >= 30 || categories.contains(WEARABLE_CONFIGURATION_CATEGORY)) {
                        actionToActivities.getOrPut(action) { mutableListOf() }.add(activity)
                    }
                }
            }
        }

        for (action in requiredActions) {
            val matches = actionToActivities[action] ?: emptyList()
            when {
                matches.isEmpty() -> {
                    for (metaData in actionToMetaElements[action].orEmpty()) {
                        context.report(
                            ISSUE,
                            metaData,
                            context.getLocation(metaData),
                            "Missing watch face configuration activity for action `$action`"
                        )
                    }
                }
                matches.size > 1 -> {
                    for (activity in matches) {
                        context.report(
                            ISSUE,
                            activity,
                            context.getLocation(activity),
                            "Duplicate watch face configuration activity for action `$action`"
                        )
                    }
                }
            }
        }
    }

    private fun Element.descendants(tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val stack = mutableListOf(this)
        while (stack.isNotEmpty()) {
            val element = stack.removeAt(stack.lastIndex)
            if (element.tagName == tag) result.add(element)
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) stack.add(child)
            }
        }
        return result
    }

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION_NAME =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val SHORT_CONFIGURATION_ACTION_NAME = "wearableConfigurationAction"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                When a watch face service declares a `wearableConfigurationAction` metadata value, \
                there must be exactly one activity in the same package with a matching intent filter. \
                On devices running API level 29 and lower, that intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
                """,
            moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}