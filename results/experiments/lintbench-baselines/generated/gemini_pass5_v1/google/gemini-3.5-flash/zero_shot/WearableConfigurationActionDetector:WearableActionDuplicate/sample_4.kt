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
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val minSdk = context.project.minSdkVersion.apiLevel

        val servicesList = mutableListOf<Element>()
        val servicesNodes = document.getElementsByTagName("service")
        for (i in 0 until servicesNodes.length) {
            servicesList.add(servicesNodes.item(i) as Element)
        }

        val activitiesList = mutableListOf<Element>()
        val activitiesNodes = document.getElementsByTagName("activity")
        for (i in 0 until activitiesNodes.length) {
            activitiesList.add(activitiesNodes.item(i) as Element)
        }

        val servicesWithMetadata = servicesList.filter { hasWearableConfigMetadata(it) }
        val matchingActivities = activitiesList.filter { isWearableConfigActivity(it, minSdk) }

        val hasServiceWithMetadata = servicesWithMetadata.isNotEmpty()
        val activityCount = matchingActivities.size

        if (hasServiceWithMetadata) {
            if (activityCount == 0) {
                val location = context.getLocation(servicesWithMetadata.first())
                context.report(
                    ISSUE,
                    servicesWithMetadata.first(),
                    location,
                    "Watch face service defines wearableConfigurationAction but no matching configuration activity was found."
                )
            } else if (activityCount > 1) {
                for (activity in matchingActivities) {
                    val location = context.getLocation(activity)
                    context.report(
                        ISSUE,
                        activity,
                        location,
                        "Multiple watch face configuration activities found."
                    )
                }
            }
        } else {
            if (activityCount > 0) {
                for (activity in matchingActivities) {
                    val location = context.getLocation(activity)
                    context.report(
                        ISSUE,
                        activity,
                        location,
                        "Watch face configuration activity found, but no watch face service defines the wearableConfigurationAction metadata."
                    )
                }
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val metaDatas = service.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val metaData = metaDatas.item(i) as Element
            val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
            if ((name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction") &&
                (value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR")) {
                return true
            }
        }
        return false
    }

    private fun isWearableConfigActivity(activity: Element, minSdk: Int): Boolean {
        val intentFilters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            if (matchesFilter(filter, minSdk)) {
                return true
            }
        }
        return false
    }

    private fun matchesFilter(filter: Element, minSdk: Int): Boolean {
        val actions = filter.getElementsByTagName("action")
        var hasAction = false
        for (i in 0 until actions.length) {
            val action = actions.item(i) as Element
            val name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || name == "WATCH_FACE_EDITOR") {
                hasAction = true
                break
            }
        }
        if (!hasAction) return false

        if (minSdk < 30) {
            val categories = filter.getElementsByTagName("category")
            var hasCategory = false
            for (i in 0 until categories.length) {
                val cat = categories.item(i) as Element
                val name = cat.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || name == "WEARABLE_CONFIGURATION") {
                    hasCategory = true
                    break
                }
            }
            return hasCategory
        }
        return true
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).
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
}