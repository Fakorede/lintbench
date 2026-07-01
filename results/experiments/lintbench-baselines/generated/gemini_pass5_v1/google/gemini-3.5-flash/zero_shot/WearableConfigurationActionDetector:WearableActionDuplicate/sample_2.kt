package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                Each watch face service that specifies a `wearableConfigurationAction` metadata \
                should have exactly one corresponding configuration activity in the same package \
                with a matching intent filter. If minSdkVersion is less than 30, the intent \
                filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val WEARABLE_CONFIG_ACTION_META = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WEARABLE_CONFIG_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("manifest")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val application = root.getElementsByTagName("application").item(0) as? Element ?: return

        val serviceConfigs = mutableListOf<ServiceConfig>()
        val activityFilters = mutableListOf<ActivityFilter>()

        // Traverse services to find watch face metadata
        val serviceNodes = application.getElementsByTagName("service")
        for (i in 0 until serviceNodes.length) {
            val service = serviceNodes.item(i) as Element
            var child = service.firstChild
            while (child != null) {
                if (child is Element && child.tagName == "meta-data") {
                    val name = child.getAndroidAttribute("name")
                    if (name == WEARABLE_CONFIG_ACTION_META) {
                        val value = child.getAndroidAttribute("value")
                        if (!value.isNullOrEmpty()) {
                            serviceConfigs.add(ServiceConfig(service, child, value))
                        }
                    }
                }
                child = child.nextSibling
            }
        }

        // Traverse activities to find intent filters
        val activityNodes = application.getElementsByTagName("activity")
        for (i in 0 until activityNodes.length) {
            val activity = activityNodes.item(i) as Element
            var child = activity.firstChild
            while (child != null) {
                if (child is Element && child.tagName == "intent-filter") {
                    val actions = mutableListOf<String>()
                    val categories = mutableListOf<String>()

                    var filterChild = child.firstChild
                    while (filterChild != null) {
                        if (filterChild is Element) {
                            if (filterChild.tagName == "action") {
                                val name = filterChild.getAndroidAttribute("name")
                                if (!name.isNullOrEmpty()) {
                                    actions.add(name)
                                }
                            } else if (filterChild.tagName == "category") {
                                val name = filterChild.getAndroidAttribute("name")
                                if (!name.isNullOrEmpty()) {
                                    categories.add(name)
                                }
                            }
                        }
                        filterChild = filterChild.nextSibling
                    }

                    if (actions.isNotEmpty()) {
                        activityFilters.add(ActivityFilter(activity, child, actions, categories))
                    }
                }
                child = child.nextSibling
            }
        }

        val minSdk = context.project.minSdkVersion.apiLevel

        // Validate matches
        for (config in serviceConfigs) {
            val matchingActivities = activityFilters.filter { filter ->
                filter.actions.contains(config.actionValue)
            }

            if (matchingActivities.isEmpty()) {
                context.report(
                    ISSUE,
                    config.metaDataElement,
                    context.getLocation(config.metaDataElement),
                    "No activity found with an intent filter for action `${config.actionValue}`"
                )
            } else if (matchingActivities.size > 1) {
                for (match in matchingActivities) {
                    context.report(
                        ISSUE,
                        match.activityElement,
                        context.getLocation(match.activityElement),
                        "Duplicate watch face configuration activities found for action `${config.actionValue}`"
                    )
                }
            } else {
                val match = matchingActivities.first()
                if (minSdk < 30) {
                    if (!match.categories.contains(WEARABLE_CONFIG_CATEGORY)) {
                        context.report(
                            ISSUE,
                            match.intentFilterElement,
                            context.getLocation(match.intentFilterElement),
                            "Intent filter for watch face configuration must include category `$WEARABLE_CONFIG_CATEGORY` when minSdkVersion < 30"
                        )
                    }
                }
            }
        }

        // Verify the reverse: if an activity registration implies it is a config activity,
        // there must be a service specifying it.
        for (filter in activityFilters) {
            val hasWearableCategory = filter.categories.contains(WEARABLE_CONFIG_CATEGORY)
            if (hasWearableCategory) {
                val matchingService = serviceConfigs.any { config ->
                    filter.actions.contains(config.actionValue)
                }
                if (!matchingService) {
                    context.report(
                        ISSUE,
                        filter.intentFilterElement,
                        context.getLocation(filter.intentFilterElement),
                        "Activity has `$WEARABLE_CONFIG_CATEGORY` category but no watch face service defines a matching `wearableConfigurationAction` metadata"
                    )
                }
            }
        }
    }

    private fun Element.getAndroidAttribute(name: String): String? {
        return if (hasAttributeNS(ANDROID_URI, name)) {
            getAttributeNS(ANDROID_URI, name)
        } else {
            getAttribute("android:$name").takeIf { it.isNotEmpty() }
        }
    }

    private class ServiceConfig(
        val serviceElement: Element,
        val metaDataElement: Element,
        val actionValue: String
    )

    private class ActivityFilter(
        val activityElement: Element,
        val intentFilterElement: Element,
        val actions: List<String>,
        val categories: List<String>
    )
}