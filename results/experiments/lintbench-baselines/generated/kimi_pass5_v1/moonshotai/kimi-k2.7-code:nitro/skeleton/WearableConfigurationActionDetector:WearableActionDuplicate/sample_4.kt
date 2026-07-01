package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

private typealias DomElement = org.w3c.dom.Element
private typealias DomNodeList = org.w3c.dom.NodeList

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val META_DATA_NAME = "wearableConfigurationAction"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                A watch face service that declares a wearableConfigurationAction
                metadata value must have exactly one activity in the same package that
                handles that action in an intent filter. Declaring more than one such
                activity is an error.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val document = context.client.readManifest(context.project) ?: return
        val manifest = document.documentElement ?: return
        val application = manifest.firstChildOrNull("application") ?: return

        val configurationActions = mutableSetOf<String>()
        application.children("service").forEach { service ->
            service.children("meta-data").forEach { metaData ->
                if (metaData.attributeValue("name") == META_DATA_NAME) {
                    metaData.attributeValue("value")?.let { configurationActions.add(it) }
                }
            }
        }

        if (configurationActions.isEmpty()) {
            return
        }

        val requireWearableCategory = context.project.minSdk < 30

        val activitiesByAction = mutableMapOf<String, MutableList<DomElement>>()
        application.children("activity").forEach { activity ->
            configurationActions.forEach { action ->
                if (activity.hasMatchingIntentFilter(action, requireWearableCategory)) {
                    activitiesByAction.getOrPut(action) { mutableListOf() }.add(activity)
                }
            }
        }

        activitiesByAction.forEach { (action, activities) ->
            if (activities.size > 1) {
                activities.forEach { activity ->
                    context.report(
                        ISSUE,
                        context.getLocation(activity),
                        "Duplicate watch face configuration activities found for action \"$action\"",
                    )
                }
            }
        }
    }

    private fun DomElement.firstChildOrNull(tag: String): DomElement? {
        val list = getElementsByTagName(tag)
        return (0 until list.length)
            .mapNotNull { list.item(it) as? DomElement }
            .firstOrNull()
    }

    private fun DomElement.children(tag: String): List<DomElement> {
        val list = getElementsByTagName(tag)
        return (0 until list.length).mapNotNull { list.item(it) as? DomElement }
    }

    private fun DomElement.attributeValue(name: String): String? =
        getAttributeNS(ANDROID_NS, name).takeIf { it.isNotEmpty() }

    private fun DomElement.hasMatchingIntentFilter(
        action: String,
        requireWearableCategory: Boolean,
    ): Boolean {
        return children("intent-filter").any { filter ->
            filter.hasChildWithAttributeValue("action", "name", action) &&
                (!requireWearableCategory ||
                    filter.hasChildWithAttributeValue(
                        "category",
                        "name",
                        CATEGORY_WEARABLE_CONFIGURATION,
                    ))
        }
    }

    private fun DomElement.hasChildWithAttributeValue(
        tag: String,
        attribute: String,
        value: String,
    ): Boolean = children(tag).any { it.attributeValue(attribute) == value }
}