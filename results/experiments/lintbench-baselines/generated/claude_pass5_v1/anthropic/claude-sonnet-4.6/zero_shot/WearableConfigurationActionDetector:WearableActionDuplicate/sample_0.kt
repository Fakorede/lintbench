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
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_META =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                minSdkVersion is less than 30).

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

    // Per-file state
    private var minSdkVersion: Int = 1
    private var hasServiceWithMetadata: Boolean = false
    private var serviceElement: Element? = null
    private var activityWithWatchFaceEditorFilter: Element? = null
    private var activityWithBothFilters: Element? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_USES_SDK,
            TAG_SERVICE,
            TAG_ACTIVITY
        )
    }

    override fun beforeCheckFile(context: Context) {
        minSdkVersion = 1
        hasServiceWithMetadata = false
        serviceElement = null
        activityWithWatchFaceEditorFilter = null
        activityWithBothFilters = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            TAG_SERVICE -> {
                if (serviceHasWatchFaceEditorMetadata(element)) {
                    hasServiceWithMetadata = true
                    serviceElement = element
                }
            }
            TAG_ACTIVITY -> {
                val (hasWatchFaceEditor, hasWearableConfig) = activityFilterInfo(element)
                if (hasWatchFaceEditor) {
                    activityWithWatchFaceEditorFilter = element
                }
                if (hasWatchFaceEditor && hasWearableConfig) {
                    activityWithBothFilters = element
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (!context.file.name.equals("AndroidManifest.xml", ignoreCase = true)) return

        if (!hasServiceWithMetadata) {
            // No service with wearableConfigurationAction = WATCH_FACE_EDITOR
            // If there's an activity with the editor filter, that's a problem
            if (activityWithWatchFaceEditorFilter != null) {
                context.report(
                    ISSUE,
                    activityWithWatchFaceEditorFilter!!,
                    context.getLocation(activityWithWatchFaceEditorFilter!!),
                    "Activity has `$WATCH_FACE_EDITOR_ACTION` intent filter but no watch face " +
                        "service defines `wearableConfigurationAction` metadata with value " +
                        "`WATCH_FACE_EDITOR`"
                )
            }
            return
        }

        // We have a service with the metadata
        if (minSdkVersion >= 30) {
            // Only need WATCH_FACE_EDITOR action filter
            if (activityWithWatchFaceEditorFilter == null) {
                context.report(
                    ISSUE,
                    serviceElement!!,
                    context.getLocation(serviceElement!!),
                    "Watch face service has `wearableConfigurationAction` metadata with value " +
                        "`WATCH_FACE_EDITOR` but no activity in the package has an intent filter " +
                        "for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        } else {
            // Need both WATCH_FACE_EDITOR action and WEARABLE_CONFIGURATION category
            if (activityWithBothFilters == null) {
                if (activityWithWatchFaceEditorFilter != null) {
                    // Has editor filter but missing category
                    context.report(
                        ISSUE,
                        activityWithWatchFaceEditorFilter!!,
                        context.getLocation(activityWithWatchFaceEditorFilter!!),
                        "Activity has `$WATCH_FACE_EDITOR_ACTION` intent filter but is missing " +
                            "the `$WEARABLE_CONFIGURATION_CATEGORY` category (required when " +
                            "minSdkVersion < 30)"
                    )
                } else {
                    context.report(
                        ISSUE,
                        serviceElement!!,
                        context.getLocation(serviceElement!!),
                        "Watch face service has `wearableConfigurationAction` metadata with value " +
                            "`WATCH_FACE_EDITOR` but no activity in the package has an intent " +
                            "filter for `$WATCH_FACE_EDITOR_ACTION` with category " +
                            "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                    )
                }
            }
        }
    }

    private fun serviceHasWatchFaceEditorMetadata(serviceElement: Element): Boolean {
        var child = serviceElement.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "meta-data") {
                    val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val value = childElement.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (name == WEARABLE_CONFIGURATION_ACTION_META &&
                        value == WATCH_FACE_EDITOR_ACTION
                    ) {
                        return true
                    }
                }
            }
            child = child.nextSibling
        }
        return false
    }

    /**
     * Returns a pair: (hasWatchFaceEditorAction, hasWearableConfigCategory)
     * for the intent filters of the given activity element.
     */
    private fun activityFilterInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigCategory = false

        var child = activityElement.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == TAG_INTENT_FILTER) {
                    val (action, category) = intentFilterInfo(childElement)
                    if (action) hasWatchFaceEditorAction = true
                    if (category) hasWearableConfigCategory = true
                }
            }
            child = child.nextSibling
        }

        return Pair(hasWatchFaceEditorAction, hasWearableConfigCategory)
    }

    /**
     * Returns (hasWatchFaceEditorAction, hasWearableConfigCategory) for a single intent-filter.
     */
    private fun intentFilterInfo(intentFilterElement: Element): Pair<Boolean, Boolean> {
        var hasAction = false
        var hasCategory = false

        var child = intentFilterElement.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                when (childElement.tagName) {
                    "action" -> {
                        if (name == WATCH_FACE_EDITOR_ACTION) hasAction = true
                    }
                    "category" -> {
                        if (name == WEARABLE_CONFIGURATION_CATEGORY) hasCategory = true
                    }
                }
            }
            child = child.nextSibling
        }

        return Pair(hasAction, hasCategory)
    }
}