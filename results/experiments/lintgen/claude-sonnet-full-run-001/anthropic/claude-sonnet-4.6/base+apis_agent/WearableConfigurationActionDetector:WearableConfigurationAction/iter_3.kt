package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
import com.android.resources.ResourceFolderType
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
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
        private const val TAG_META_DATA = "meta-data"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there must be an activity in the same package that has \
                an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, the \
                activity's intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.

                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val manifestElement = document.documentElement ?: return

        // Get minSdkVersion
        var minSdkVersion = 1
        val usesSdkList = manifestElement.getElementsByTagName(TAG_USES_SDK)
        for (i in 0 until usesSdkList.length) {
            val usesSdk = usesSdkList.item(i) as? Element ?: continue
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                minSdkVersion = minSdk.toIntOrNull() ?: 1
            }
        }

        // Find all services with wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val servicesWithConfigAction = mutableListOf<Element>()
        val serviceList = manifestElement.getElementsByTagName(TAG_SERVICE)
        for (i in 0 until serviceList.length) {
            val service = serviceList.item(i) as? Element ?: continue
            if (hasWearableConfigurationActionMetadata(service)) {
                servicesWithConfigAction.add(service)
            }
        }

        // Find all activities and their intent filter info
        data class ActivityInfo(
            val element: Element,
            val hasWatchFaceEditorAction: Boolean,
            val hasWearableConfigurationCategory: Boolean
        )

        val allActivities = mutableListOf<ActivityInfo>()
        val activityList = manifestElement.getElementsByTagName(TAG_ACTIVITY)
        for (i in 0 until activityList.length) {
            val activity = activityList.item(i) as? Element ?: continue
            val (hasAction, hasCategory) = getIntentFilterInfo(activity)
            allActivities.add(ActivityInfo(activity, hasAction, hasCategory))
        }

        val activitiesWithEditorAction = allActivities.filter { it.hasWatchFaceEditorAction }

        // Case 1: Service has metadata but no activity has the WATCH_FACE_EDITOR action
        for (service in servicesWithConfigAction) {
            if (activitiesWithEditorAction.isEmpty()) {
                // Report on the service - no activity has the required intent filter
                context.report(
                    ISSUE,
                    context.getLocation(service),
                    "Watch face service defines `wearableConfigurationAction` metadata but " +
                        "no activity in the same package has an intent filter for " +
                        "`$WATCH_FACE_EDITOR_ACTION`"
                )
            } else {
                // Check if minSdkVersion < 30 and activities are missing the category
                if (minSdkVersion < 30) {
                    for (activity in activitiesWithEditorAction) {
                        if (!activity.hasWearableConfigurationCategory) {
                            context.report(
                                ISSUE,
                                context.getLocation(activity.element),
                                "Activity with `$WATCH_FACE_EDITOR_ACTION` intent filter must also " +
                                    "include the `$WEARABLE_CONFIGURATION_CATEGORY` category when " +
                                    "`minSdkVersion` is less than 30"
                            )
                        }
                    }
                }
            }
        }

        // Case 2: Activity has WATCH_FACE_EDITOR action but no service has the metadata
        if (servicesWithConfigAction.isEmpty()) {
            for (activity in activitiesWithEditorAction) {
                context.report(
                    ISSUE,
                    context.getLocation(activity.element),
                    "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION` but " +
                        "no watch face service in the same package defines " +
                        "`wearableConfigurationAction` metadata"
                )
            }
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        var child = serviceElement.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_META_DATA) {
                val metaElement = child as Element
                val name = metaElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaElement.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    return true
                }
            }
            child = child.nextSibling
        }
        return false
    }

    private fun getIntentFilterInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigurationCategory = false

        var child = activityElement.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_INTENT_FILTER) {
                val intentFilter = child as Element
                var filterChild = intentFilter.firstChild
                while (filterChild != null) {
                    if (filterChild.nodeType == Node.ELEMENT_NODE) {
                        val filterElement = filterChild as Element
                        when (filterElement.tagName) {
                            TAG_ACTION -> {
                                val actionName =
                                    filterElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                    hasWatchFaceEditorAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                val categoryName =
                                    filterElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                    hasWearableConfigurationCategory = true
                                }
                            }
                        }
                    }
                    filterChild = filterChild.nextSibling
                }
            }
            child = child.nextSibling
        }

        return Pair(hasWatchFaceEditorAction, hasWearableConfigurationCategory)
    }
}