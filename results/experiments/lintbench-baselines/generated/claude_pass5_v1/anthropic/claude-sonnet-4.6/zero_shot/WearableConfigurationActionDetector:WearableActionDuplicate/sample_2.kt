package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same \
                package which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                minSdkVersion is less than 30).

                See https://developer.android.com/training/wearables/watch-faces/configuration \
                for more details.
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

    // Per-file state
    private var minSdkVersion: Int = 1
    private val watchFaceEditorServices = mutableListOf<Element>()
    private val watchFaceEditorActivities = mutableListOf<Element>()

    override fun beforeCheckFile(context: Context) {
        minSdkVersion = 1
        watchFaceEditorServices.clear()
        watchFaceEditorActivities.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_SDK, TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdkAttr = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdkAttr.isNotEmpty()) {
                    minSdkVersion = minSdkAttr.toIntOrNull() ?: 1
                }
            }
            TAG_SERVICE -> {
                if (serviceHasWatchFaceEditorMetadata(element)) {
                    watchFaceEditorServices.add(element)
                }
            }
            TAG_ACTIVITY -> {
                if (activityHasWatchFaceEditorIntentFilter(element)) {
                    watchFaceEditorActivities.add(element)
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (watchFaceEditorServices.isEmpty()) {
            // No watch face service with wearableConfigurationAction metadata - nothing to check
            return
        }

        val xmlContext = context as? XmlContext ?: return

        if (watchFaceEditorActivities.size > 1) {
            // Multiple activities found - report error on each duplicate
            for (activity in watchFaceEditorActivities) {
                xmlContext.report(
                    ISSUE,
                    activity,
                    xmlContext.getElementLocation(activity),
                    "Duplicate watch face configuration activities found: there should be " +
                        "exactly one activity with an intent filter for `WATCH_FACE_EDITOR`"
                )
            }
        } else if (watchFaceEditorActivities.isEmpty()) {
            // No activity found - report error on each service
            for (service in watchFaceEditorServices) {
                val requiresCategory = minSdkVersion < 30
                val message = if (requiresCategory) {
                    "Watch face service defines `wearableConfigurationAction` metadata with " +
                        "`WATCH_FACE_EDITOR` but no activity with an intent filter for " +
                        "`WATCH_FACE_EDITOR` and category `WEARABLE_CONFIGURATION` was found"
                } else {
                    "Watch face service defines `wearableConfigurationAction` metadata with " +
                        "`WATCH_FACE_EDITOR` but no activity with an intent filter for " +
                        "`WATCH_FACE_EDITOR` was found"
                }
                xmlContext.report(
                    ISSUE,
                    service,
                    xmlContext.getElementLocation(service),
                    message
                )
            }
        }
        // Exactly one activity found - this is the correct case, no error
    }

    /**
     * Returns true if the given service element has metadata with name
     * `wearableConfigurationAction` and value `WATCH_FACE_EDITOR`.
     */
    private fun serviceHasWatchFaceEditorMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "meta-data") {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true if the given activity element has an intent filter with action
     * `WATCH_FACE_EDITOR` (and category `WEARABLE_CONFIGURATION` if minSdkVersion < 30).
     */
    private fun activityHasWatchFaceEditorIntentFilter(activityElement: Element): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                if (intentFilterHasWatchFaceEditorAction(child)) {
                    if (minSdkVersion >= 30) {
                        return true
                    } else {
                        if (intentFilterHasWearableConfigurationCategory(child)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    /**
     * Returns true if the intent filter has an action element with name `WATCH_FACE_EDITOR`.
     */
    private fun intentFilterHasWatchFaceEditorAction(intentFilterElement: Element): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "action") {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == WATCH_FACE_EDITOR_ACTION) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true if the intent filter has a category element with name
     * `WEARABLE_CONFIGURATION`.
     */
    private fun intentFilterHasWearableConfigurationCategory(intentFilterElement: Element): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "category") {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                    return true
                }
            }
        }
        return false
    }
}