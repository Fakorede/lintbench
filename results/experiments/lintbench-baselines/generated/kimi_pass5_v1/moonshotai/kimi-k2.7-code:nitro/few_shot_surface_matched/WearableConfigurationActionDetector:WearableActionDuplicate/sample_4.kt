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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
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
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val serviceActions = mutableMapOf<String, MutableList<ServiceAction>>()
    private val activityActions = mutableMapOf<String, MutableList<ActivityAction>>()

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun beforeCheckRootProject(context: Context) {
        serviceActions.clear()
        activityActions.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val packageName = getManifestPackage(element)

        when (element.tagName) {
            TAG_SERVICE -> {
                var metaData = getFirstSubTagByName(element, TAG_META_DATA)
                while (metaData != null) {
                    val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == "wearableConfigurationAction") {
                        val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                        if (value.isNotBlank()) {
                            serviceActions.getOrPut(packageName) { mutableListOf() }.add(
                                ServiceAction(packageName, value, element, metaData)
                            )
                        }
                    }
                    metaData = getNextTagByName(metaData, TAG_META_DATA)
                }
            }
            TAG_ACTIVITY -> {
                val activityLocation = context.getElementLocation(element)
                var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
                while (intentFilter != null) {
                    val hasWearableCategory = hasWearableConfigurationCategory(intentFilter)
                    var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
                    while (action != null) {
                        val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (actionName.isNotBlank()) {
                            activityActions.getOrPut(packageName) { mutableListOf() }.add(
                                ActivityAction(
                                    packageName,
                                    actionName,
                                    element,
                                    activityLocation,
                                    hasWearableCategory
                                )
                            )
                        }
                        action = getNextTagByName(action, TAG_ACTION)
                    }
                    intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
                }
            }
        }
    }

    private fun hasWearableConfigurationCategory(intentFilter: Element): Boolean {
        var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
        while (category != null) {
            if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) ==
                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
            ) {
                return true
            }
            category = getNextTagByName(category, TAG_CATEGORY)
        }
        return false
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.mainProject.minSdkVersion.featureLevel

        for ((packageName, services) in serviceActions) {
            val activities = activityActions[packageName] ?: continue

            for (service in services) {
                val action = service.action
                if (action != "WATCH_FACE_EDITOR") {
                    continue
                }

                val matches = activities.filter {
                    it.action == action && (minSdk >= 30 || it.hasWearableCategory)
                }

                val distinctActivities = matches.distinctBy { it.activityElement }
                if (distinctActivities.size > 1) {
                    for (activity in distinctActivities) {
                        context.report(
                            ISSUE,
                            activity.location,
                            "Duplicate watch face configuration activities found"
                        )
                    }
                }
            }
        }
    }

    private fun getManifestPackage(element: Element): String {
        var node: Node? = element
        while (node != null) {
            if (node is Element && node.tagName == TAG_MANIFEST) {
                return node.getAttribute(ATTR_PACKAGE)
            }
            node = node.parentNode
        }
        return ""
    }

    private data class ServiceAction(
        val packageName: String,
        val action: String,
        val serviceElement: Element,
        val metaDataElement: Element
    )

    private data class ActivityAction(
        val packageName: String,
        val action: String,
        val activityElement: Element,
        val location: Location,
        val hasWearableCategory: Boolean
    )

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service declares a `wearableConfigurationAction` metadata " +
                "value of `WATCH_FACE_EDITOR`, there should be exactly one activity in the same package " +
                "with an intent filter for `WATCH_FACE_EDITOR`. When `minSdkVersion` is less than 30, " +
                "that intent filter must also include the category " +
                "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.",
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