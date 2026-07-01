package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
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
import com.android.utils.XmlUtils
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION = "WATCH_FACE_EDITOR"
        private const val META_DATA_WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there should be an activity in the same package \
                that has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less \
                than 30, the intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.

                See https://developer.android.com/training/wearables/watch-faces/configuration \
                for more details.
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

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val manifestDocument: Document = mainProject.mergedManifest ?: return

        val root = manifestDocument.documentElement ?: return

        // Get minSdkVersion
        val minSdkVersion = getMinSdkVersion(root)

        // Get application element
        val applicationElement = XmlUtils.getFirstSubTagByName(root, TAG_APPLICATION) ?: return

        // Find all services with wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val servicesWithConfig = mutableListOf<Element>()
        var serviceElement = XmlUtils.getFirstSubTagByName(applicationElement, TAG_SERVICE)
        while (serviceElement != null) {
            if (hasWearableConfigurationActionMetadata(serviceElement)) {
                servicesWithConfig.add(serviceElement)
            }
            serviceElement = XmlUtils.getNextTagByName(serviceElement, TAG_SERVICE)
        }

        if (servicesWithConfig.isEmpty()) {
            return
        }

        // Collect all activities and check their intent filters
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()
        val activitiesWithWatchFaceEditorAndCategory = mutableListOf<Element>()

        var activityElement = XmlUtils.getFirstSubTagByName(applicationElement, TAG_ACTIVITY)
        while (activityElement != null) {
            val (hasAction, hasCategory) = checkActivityIntentFilters(activityElement)
            if (hasAction) {
                activitiesWithWatchFaceEditor.add(activityElement)
                if (hasCategory) {
                    activitiesWithWatchFaceEditorAndCategory.add(activityElement)
                }
            }
            activityElement = XmlUtils.getNextTagByName(activityElement, TAG_ACTIVITY)
        }

        // For each service with the metadata, report issues if no matching activity found
        for (service in servicesWithConfig) {
            if (minSdkVersion < 30) {
                // Need activity with both WATCH_FACE_EDITOR action AND WEARABLE_CONFIGURATION category
                if (activitiesWithWatchFaceEditorAndCategory.isEmpty()) {
                    if (activitiesWithWatchFaceEditor.isEmpty()) {
                        // No matching activity at all
                        reportIssue(
                            context,
                            service,
                            "Watch face service has `wearableConfigurationAction` metadata but " +
                                "no activity in the package has an intent filter for " +
                                "`WATCH_FACE_EDITOR` with the " +
                                "`$CATEGORY_WEARABLE_CONFIGURATION` category " +
                                "(required when minSdkVersion < 30)"
                        )
                    } else {
                        // Has activity with action but missing category
                        reportIssue(
                            context,
                            service,
                            "Watch face service has `wearableConfigurationAction` metadata but " +
                                "the matching activity's intent filter is missing the category " +
                                "`$CATEGORY_WEARABLE_CONFIGURATION` " +
                                "(required when minSdkVersion < 30)"
                        )
                    }
                }
            } else {
                // Only need activity with WATCH_FACE_EDITOR action
                if (activitiesWithWatchFaceEditor.isEmpty()) {
                    reportIssue(
                        context,
                        service,
                        "Watch face service has `wearableConfigurationAction` metadata but " +
                            "no activity in the package has an intent filter for " +
                            "`WATCH_FACE_EDITOR`"
                    )
                }
            }
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        var metaData = XmlUtils.getFirstSubTagByName(serviceElement, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION &&
                value == WEARABLE_CONFIGURATION_ACTION
            ) {
                return true
            }
            metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    /**
     * Returns a pair (hasWatchFaceEditorAction, hasWearableConfigurationCategory)
     */
    private fun checkActivityIntentFilters(activityElement: Element): Pair<Boolean, Boolean> {
        var hasAction = false
        var hasCategory = false

        var intentFilter = XmlUtils.getFirstSubTagByName(activityElement, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var filterHasAction = false
            var filterHasCategory = false

            var action = XmlUtils.getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == WEARABLE_CONFIGURATION_ACTION) {
                    filterHasAction = true
                }
                action = XmlUtils.getNextTagByName(action, TAG_ACTION)
            }

            var category = XmlUtils.getFirstSubTagByName(intentFilter, TAG_CATEGORY)
            while (category != null) {
                val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (categoryName == CATEGORY_WEARABLE_CONFIGURATION) {
                    filterHasCategory = true
                }
                category = XmlUtils.getNextTagByName(category, TAG_CATEGORY)
            }

            if (filterHasAction) {
                hasAction = true
                if (filterHasCategory) {
                    hasCategory = true
                }
            }

            intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }

        return Pair(hasAction, hasCategory)
    }

    private fun getMinSdkVersion(root: Element): Int {
        val usesSdk = XmlUtils.getFirstSubTagByName(root, TAG_USES_SDK)
        if (usesSdk != null) {
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                return minSdk.toIntOrNull() ?: 1
            }
        }
        return 1
    }

    private fun reportIssue(context: Context, element: Element, message: String) {
        val location = try {
            val xmlContext = context as? XmlContext
            xmlContext?.getLocation(element) ?: Location.create(context.file)
        } catch (e: Exception) {
            Location.create(context.file)
        }
        context.report(ISSUE, location, message)
    }
}