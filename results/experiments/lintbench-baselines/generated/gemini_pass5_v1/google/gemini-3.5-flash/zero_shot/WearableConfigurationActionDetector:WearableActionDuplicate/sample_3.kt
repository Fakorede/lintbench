package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                Each watch face service should have at most one configuration activity. \
                If a watch face service defines the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there should be exactly one activity \
                in the same package with the corresponding intent filter.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    private class ConfigActivity(
        val element: Element,
        val actionElement: Element,
        val hasCategory: Boolean,
        val intentFilterElement: Element
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val application = element.getElementsByTagName("application").item(0) as? Element ?: return

        val servicesWithConfig = mutableListOf<Pair<Element, Element>>()
        val services = application.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDataList = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDataList.length) {
                val metaData = metaDataList.item(j) as Element
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, "value")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                    value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                    servicesWithConfig.add(Pair(service, metaData))
                }
            }
        }

        val configActivities = mutableListOf<ConfigActivity>()
        val activities = application.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                var matchAction: Element? = null
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    if (action.getAttributeNS(SdkConstants.ANDROID_URI, "name") == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                        matchAction = action
                        break
                    }
                }
                if (matchAction != null) {
                    val categories = filter.getElementsByTagName("category")
                    var hasCategory = false
                    for (k in 0 until categories.length) {
                        val category = categories.item(k) as Element
                        if (category.getAttributeNS(SdkConstants.ANDROID_URI, "name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                            break
                        }
                    }
                    configActivities.add(ConfigActivity(activity, matchAction, hasCategory, filter))
                }
            }
        }

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        // 1. Duplicate check
        if (configActivities.size > 1) {
            for (configActivity in configActivities) {
                context.report(
                    ISSUE,
                    configActivity.element,
                    context.getNameLocation(configActivity.element),
                    "Duplicate watch face configuration activities found. There should be at most one activity with the WATCH_FACE_EDITOR intent filter."
                )
            }
        }

        // 2. Service defined configuration metadata, but no activity has the configuration action
        if (servicesWithConfig.isNotEmpty() && configActivities.isEmpty()) {
            for (pair in servicesWithConfig) {
                val metaData = pair.second
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    "The watch face service defines wearableConfigurationAction, but no activity with WATCH_FACE_EDITOR intent filter was found."
                )
            }
        }

        // 3. Activity has the configuration action, but no service defines the metadata
        if (configActivities.isNotEmpty() && servicesWithConfig.isEmpty()) {
            for (configActivity in configActivities) {
                context.report(
                    ISSUE,
                    configActivity.element,
                    context.getNameLocation(configActivity.element),
                    "Found activity with WATCH_FACE_EDITOR intent filter, but no watch face service defines wearableConfigurationAction metadata."
                )
            }
        }

        // 4. Missing category when minSdkVersion < 30
        if (minSdkVersion < 30) {
            for (configActivity in configActivities) {
                if (!configActivity.hasCategory) {
                    context.report(
                        ISSUE,
                        configActivity.intentFilterElement,
                        context.getLocation(configActivity.intentFilterElement),
                        "Activities with WATCH_FACE_EDITOR intent filter must also include the WEARABLE_CONFIGURATION category when minSdkVersion is less than 30."
                    )
                }
            }
        }
    }
}