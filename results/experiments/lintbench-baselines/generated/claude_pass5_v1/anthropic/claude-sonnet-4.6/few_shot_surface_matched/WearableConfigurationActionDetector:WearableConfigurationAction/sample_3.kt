package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package that \
                has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, \
                the intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.

                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun checkMergedProject(context: XmlContext) {
        val document: Document = context.document ?: return
        val root = document.documentElement ?: return

        // Determine minSdkVersion
        val minSdkVersion = getMinSdkVersion(root)

        // Find all services that declare wearableConfigurationAction = WATCH_FACE_EDITOR
        val services = mutableListOf<Element>()
        var child = XmlUtils.getFirstSubTagByName(root, "application")
        val application = child ?: return

        var serviceElement = XmlUtils.getFirstSubTagByName(application, TAG_SERVICE)
        while (serviceElement != null) {
            if (serviceHasWatchFaceEditorConfig(serviceElement)) {
                services.add(serviceElement)
            }
            serviceElement = XmlUtils.getNextTagByName(serviceElement, TAG_SERVICE)
        }

        if (services.isEmpty()) return

        // Collect all activities that have an intent filter with WATCH_FACE_EDITOR action
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()
        var activityElement = XmlUtils.getFirstSubTagByName(application, TAG_ACTIVITY)
        while (activityElement != null) {
            if (activityHasWatchFaceEditorAction(activityElement)) {
                activitiesWithWatchFaceEditor.add(activityElement)
            }
            activityElement = XmlUtils.getNextTagByName(activityElement, TAG_ACTIVITY)
        }

        // For each service with the metadata, check that there's a matching activity
        for (service in services) {
            val metaDataElement = findWatchFaceEditorMetaData(service) ?: continue

            if (activitiesWithWatchFaceEditor.isEmpty()) {
                // No activity with WATCH_FACE_EDITOR action at all
                context.report(
                    ISSUE,
                    metaDataElement,
                    context.getLocation(metaDataElement),
                    "No activity found with an intent filter for `$WATCH_FACE_EDITOR`"
                )
                continue
            }

            if (minSdkVersion < 30) {
                // Also need WEARABLE_CONFIGURATION category
                val hasActivityWithCategory = activitiesWithWatchFaceEditor.any { activity ->
                    activityHasWearableConfigurationCategory(activity)
                }
                if (!hasActivityWithCategory) {
                    context.report(
                        ISSUE,
                        metaDataElement,
                        context.getLocation(metaDataElement),
                        "No activity found with an intent filter for `$WATCH_FACE_EDITOR` " +
                            "and category `$WEARABLE_CONFIGURATION`. " +
                            "The category is required when `minSdkVersion` is less than 30."
                    )
                }
            }
        }
    }

    private fun getMinSdkVersion(manifestRoot: Element): Int {
        var usesSdk = XmlUtils.getFirstSubTagByName(manifestRoot, TAG_USES_SDK)
        while (usesSdk != null) {
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                return minSdk.toIntOrNull() ?: 1
            }
            usesSdk = XmlUtils.getNextTagByName(usesSdk, TAG_USES_SDK)
        }
        return 1
    }

    private fun serviceHasWatchFaceEditorConfig(service: Element): Boolean {
        return findWatchFaceEditorMetaData(service) != null
    }

    private fun findWatchFaceEditorMetaData(service: Element): Element? {
        var metaData = XmlUtils.getFirstSubTagByName(service, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                return metaData
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
        return null
    }

    private fun activityHasWatchFaceEditorAction(activity: Element): Boolean {
        var intentFilter = XmlUtils.getFirstSubTagByName(activity, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var action = XmlUtils.getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == WATCH_FACE_EDITOR) {
                    return true
                }
                action = XmlUtils.getNextTagByName(action, TAG_ACTION)
            }
            intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun activityHasWearableConfigurationCategory(activity: Element): Boolean {
        var intentFilter = XmlUtils.getFirstSubTagByName(activity, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var hasWatchFaceEditorAction = false
            var hasWearableConfigCategory = false

            var action = XmlUtils.getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == WATCH_FACE_EDITOR) {
                    hasWatchFaceEditorAction = true
                }
                action = XmlUtils.getNextTagByName(action, TAG_ACTION)
            }

            var category = XmlUtils.getFirstSubTagByName(intentFilter, TAG_CATEGORY)
            while (category != null) {
                val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (categoryName == WEARABLE_CONFIGURATION) {
                    hasWearableConfigCategory = true
                }
                category = XmlUtils.getNextTagByName(category, TAG_CATEGORY)
            }

            if (hasWatchFaceEditorAction && hasWearableConfigCategory) {
                return true
            }

            intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }
}