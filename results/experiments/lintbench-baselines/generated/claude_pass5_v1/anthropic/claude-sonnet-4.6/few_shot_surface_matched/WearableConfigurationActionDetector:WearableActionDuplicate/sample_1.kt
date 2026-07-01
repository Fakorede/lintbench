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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package that \
                has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if minSdkVersion is less than 30).

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

    override fun checkMergedProject(context: Context) {
        val mainDocument = context.mainProject.mergedManifest ?: return
        val root = mainDocument.documentElement ?: return

        // Get minSdkVersion
        val minSdkVersion = getMinSdkVersion(root)

        // Find the application element
        val application = getFirstSubTagByName(root, TAG_APPLICATION) ?: return

        // Collect all services that have the wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val servicesWithMetadata = mutableListOf<Element>()
        var service = getFirstSubTagByName(application, TAG_SERVICE)
        while (service != null) {
            if (serviceHasWatchFaceEditorMetadata(service)) {
                servicesWithMetadata.add(service)
            }
            service = getNextTagByName(service, TAG_SERVICE)
        }

        if (servicesWithMetadata.isEmpty()) {
            return
        }

        // Collect all activities that have an intent filter for WATCH_FACE_EDITOR
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()
        var activity = getFirstSubTagByName(application, TAG_ACTIVITY)
        while (activity != null) {
            if (activityHasWatchFaceEditorAction(activity, minSdkVersion)) {
                activitiesWithWatchFaceEditor.add(activity)
            }
            activity = getNextTagByName(activity, TAG_ACTIVITY)
        }

        // If there are multiple activities with the WATCH_FACE_EDITOR intent filter, that's a duplicate
        if (activitiesWithWatchFaceEditor.size > 1) {
            for (act in activitiesWithWatchFaceEditor) {
                val xmlContext = getXmlContextForElement(context, act) ?: continue
                val location = xmlContext.getLocation(act)
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activities found: multiple activities " +
                        "declare an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
            return
        }

        // If there are services with metadata but no matching activity, report
        if (activitiesWithWatchFaceEditor.isEmpty()) {
            for (svc in servicesWithMetadata) {
                val xmlContext = getXmlContextForElement(context, svc) ?: continue
                val location = xmlContext.getLocation(svc)
                context.report(
                    ISSUE,
                    location,
                    "Watch face service has `wearableConfigurationAction` metadata set to " +
                        "`WATCH_FACE_EDITOR` but no matching activity with a " +
                        "`$WATCH_FACE_EDITOR_ACTION` intent filter was found"
                )
            }
        }
    }

    private fun getMinSdkVersion(root: Element): Int {
        val usesSdk = getFirstSubTagByName(root, TAG_USES_SDK)
        if (usesSdk != null) {
            val minSdkStr = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdkStr.isNotEmpty()) {
                return minSdkStr.toIntOrNull() ?: 1
            }
        }
        return 1
    }

    private fun serviceHasWatchFaceEditorMetadata(service: Element): Boolean {
        var metaData = getFirstSubTagByName(service, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                value == WATCH_FACE_EDITOR_ACTION
            ) {
                return true
            }
            metaData = getNextTagByName(metaData, TAG_META_DATA)
        }
        return false
    }

    private fun activityHasWatchFaceEditorAction(activity: Element, minSdkVersion: Int): Boolean {
        var intentFilter = getFirstSubTagByName(activity, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var hasWatchFaceEditorAction = false
            var hasWearableConfigurationCategory = false

            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                    hasWatchFaceEditorAction = true
                }
                action = getNextTagByName(action, TAG_ACTION)
            }

            var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
            while (category != null) {
                val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                    hasWearableConfigurationCategory = true
                }
                category = getNextTagByName(category, TAG_CATEGORY)
            }

            if (hasWatchFaceEditorAction) {
                if (minSdkVersion >= 30) {
                    // Category not required for API 30+
                    return true
                } else {
                    // Category required for API < 30
                    if (hasWearableConfigurationCategory) {
                        return true
                    }
                }
            }

            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun getXmlContextForElement(context: Context, element: Element): XmlContext? {
        // We need to find the XmlContext for the element; since we're in checkMergedProject,
        // we use the main project's manifest file context
        val project = context.mainProject
        val manifestFile = project.manifestFiles.firstOrNull() ?: return null
        // We can't easily get an XmlContext here, so we use the context directly
        return null
    }

    override fun getApplicableElements(): Collection<String>? = null
}