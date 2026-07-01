package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private data class ServiceConfig(
        val location: Location,
        val value: String
    )

    private data class IntentFilterInfo(
        val actions: Set<String>,
        val categories: Set<String>
    )

    private val serviceConfigs = mutableListOf<ServiceConfig>()
    private val activityFilters = mutableListOf<IntentFilterInfo>()
    private var minSdk = 1

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_SERVICE, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tag()) {
            TAG_SERVICE -> {
                val metaData = findConfigurationMetaData(element)
                if (metaData != null) {
                    val value = metaData.getAttributeValue(ATTR_VALUE)
                    if (value.isNotEmpty()) {
                        serviceConfigs.add(ServiceConfig(context.getLocation(metaData), value))
                    }
                }
            }
            TAG_ACTIVITY, TAG_ACTIVITY_ALIAS -> {
                for (child in element.childElements()) {
                    if (child.tag() != TAG_INTENT_FILTER) {
                        continue
                    }

                    val actions = mutableSetOf<String>()
                    val categories = mutableSetOf<String>()
                    for (grandChild in child.childElements()) {
                        when (grandChild.tag()) {
                            TAG_ACTION -> {
                                val name = grandChild.getAttributeValue(ATTR_NAME)
                                if (name.isNotEmpty()) {
                                    actions.add(name)
                                }
                            }
                            TAG_CATEGORY -> {
                                val name = grandChild.getAttributeValue(ATTR_NAME)
                                if (name.isNotEmpty()) {
                                    categories.add(name)
                                }
                            }
                        }
                    }
                    activityFilters.add(IntentFilterInfo(actions, categories))
                }
            }
        }
    }

    override fun beforeCheckEachProject(context: Context) {
        serviceConfigs.clear()
        activityFilters.clear()
        minSdk = context.project.minSdk
    }

    override fun afterCheckEachProject(context: Context) {
        if (serviceConfigs.isEmpty()) {
            return
        }

        for (config in serviceConfigs) {
            val (requiredAction, requiredCategory) = when (config.value) {
                WATCH_FACE_EDITOR -> Pair(
                    WATCH_FACE_EDITOR,
                    if (minSdk < 30) WEARABLE_CONFIGURATION else null
                )
                EDITOR_ACTION -> Pair(EDITOR_ACTION, WEARABLE_CONFIGURATION)
                else -> continue
            }

            val match = activityFilters.any { filter ->
                requiredAction in filter.actions &&
                    (requiredCategory == null || requiredCategory in filter.categories)
            }

            if (!match) {
                val categoryRequirement = if (requiredCategory != null) {
                    " with category `$requiredCategory`"
                } else {
                    ""
                }

                context.report(
                    ISSUE,
                    config.location,
                    "Watch face service defines a wearableConfigurationAction of `$requiredAction`, " +
                        "but no activity has an intent filter for that action$categoryRequirement."
                )
            }
        }
    }

    private fun findConfigurationMetaData(service: Element): Element? {
        for (child in service.childElements()) {
            if (child.tag() == TAG_META_DATA) {
                val name = child.getAttributeValue(ATTR_NAME)
                if (name == WEARABLE_CONFIGURATION_ACTION) {
                    return child
                }
            }
        }
        return null
    }

    private fun Element.tag(): String {
        return localName ?: tagName.substringAfter(':', tagName)
    }

    private fun Element.getAttributeValue(name: String): String {
        return getAttributeNS(ANDROID_URI, name).ifEmpty { getAttribute(name) }
    }

    private fun Element.childElements(): Sequence<Element> {
        val nodes = childNodes ?: return emptySequence()
        return (0 until nodes.length)
            .asSequence()
            .map { nodes.item(it) }
            .filterIsInstance<Element>()
    }

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR =
            "com.google.android.wearable.watchface.action.WATCH_FACE_EDITOR"
        private const val EDITOR_ACTION =
            "com.google.android.wearable.watchface.action.EDITOR_ACTION"
        private const val WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action must match an activity",
            explanation = """
                When a watch face service defines a `wearableConfigurationAction` metadata, there must be an activity in the same package with an intent filter for that action. If `minSdkVersion` is less than 30, the intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}