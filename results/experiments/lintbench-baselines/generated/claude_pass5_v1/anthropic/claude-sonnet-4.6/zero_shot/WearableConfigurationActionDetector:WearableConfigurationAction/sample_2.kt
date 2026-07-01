package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "com.google.android.wearable.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val METADATA_WEARABLE_CONFIGURATION_ACTION = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_SERVICE = "android.service.wallpaper.WallpaperService"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                `minSdkVersion` is less than 30).

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

        private const val TAG_META_DATA = "meta-data"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
    }

    // Tracks services that have the wearableConfigurationAction metadata with WATCH_FACE_EDITOR value
    private val servicesWithConfigAction = mutableListOf<Element>()

    // Tracks activities that have WATCH_FACE_EDITOR intent filter action
    private val activitiesWithEditorAction = mutableListOf<ActivityInfo>()

    override fun reset() {
        super.reset()
        servicesWithConfigAction.clear()
        activitiesWithEditorAction.clear()
    }

    private data class ActivityInfo(
        val element: Element,
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigurationCategory: Boolean
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> visitService(context, element)
            TAG_ACTIVITY -> visitActivity(context, element)
        }
    }

    private fun visitService(context: XmlContext, element: Element) {
        // Check if this service has wearableConfigurationAction metadata with WATCH_FACE_EDITOR value
        if (hasWearableConfigurationActionMetadata(element)) {
            servicesWithConfigAction.add(element)
        }
    }

    private fun visitActivity(context: XmlContext, element: Element) {
        val (hasAction, hasCategory) = getIntentFilterInfo(element)
        if (hasAction) {
            activitiesWithEditorAction.add(
                ActivityInfo(element, hasAction, hasCategory)
            )
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        var child = serviceElement.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_META_DATA) {
                val metaDataElement = child as Element
                val name = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == METADATA_WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                    return true
                }
            }
            child = child.nextSibling
        }
        return false
    }

    private fun getIntentFilterInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigCategory = false

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
                                val actionName = filterElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR) {
                                    hasWatchFaceEditorAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                val categoryName = filterElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                    hasWearableConfigCategory = true
                                }
                            }
                        }
                    }
                    filterChild = filterChild.nextSibling
                }
            }
            child = child.nextSibling
        }
        return Pair(hasWatchFaceEditorAction, hasWearableConfigCategory)
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val minSdk = context.mainProject.minSdk

        // For each service with the configuration action metadata, check if there's a matching activity
        for (serviceElement in servicesWithConfigAction) {
            val hasMatchingActivity = if (minSdk < 30) {
                // Need both WATCH_FACE_EDITOR action AND WEARABLE_CONFIGURATION category
                activitiesWithEditorAction.any { it.hasWatchFaceEditorAction && it.hasWearableConfigurationCategory }
            } else {
                // Only need WATCH_FACE_EDITOR action
                activitiesWithEditorAction.any { it.hasWatchFaceEditorAction }
            }

            if (!hasMatchingActivity) {
                val message = if (minSdk < 30) {
                    "Watch face configuration service has `wearableConfigurationAction` metadata " +
                        "with value `WATCH_FACE_EDITOR`, but no activity in the package has an " +
                        "intent filter for `$WATCH_FACE_EDITOR` with category " +
                        "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                } else {
                    "Watch face configuration service has `wearableConfigurationAction` metadata " +
                        "with value `WATCH_FACE_EDITOR`, but no activity in the package has an " +
                        "intent filter for `$WATCH_FACE_EDITOR`"
                }
                context.report(
                    ISSUE,
                    serviceElement,
                    context.getNameLocation(serviceElement),
                    message
                )
            }
        }

        // Check for activities with WATCH_FACE_EDITOR action but missing category when minSdk < 30
        if (minSdk < 30) {
            for (activityInfo in activitiesWithEditorAction) {
                if (activityInfo.hasWatchFaceEditorAction && !activityInfo.hasWearableConfigurationCategory) {
                    // Only report if there's a service requiring it
                    if (servicesWithConfigAction.isNotEmpty()) {
                        context.report(
                            ISSUE,
                            activityInfo.element,
                            context.getNameLocation(activityInfo.element),
                            "Activity has intent filter for `$WATCH_FACE_EDITOR` but is missing " +
                                "the `$WEARABLE_CONFIGURATION_CATEGORY` category, which is required " +
                                "when minSdkVersion < 30"
                        )
                    }
                }
            }
        }

        servicesWithConfigAction.clear()
        activitiesWithEditorAction.clear()
    }
}