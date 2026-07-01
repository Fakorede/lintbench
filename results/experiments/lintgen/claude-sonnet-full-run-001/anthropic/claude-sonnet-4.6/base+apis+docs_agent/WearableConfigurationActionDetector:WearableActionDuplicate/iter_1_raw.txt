package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same \
                package that has an intent filter for `WATCH_FACE_EDITOR` (and \
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

    // Per-manifest state
    private var minSdkVersion: Int = 1
    private val watchFaceEditorServices = mutableListOf<Element>()
    private val watchFaceEditorActivities = mutableListOf<Element>()
    private var xmlContext: XmlContext? = null

    override fun beforeCheckFile(context: Context) {
        minSdkVersion = 1
        watchFaceEditorServices.clear()
        watchFaceEditorActivities.clear()
        xmlContext = null
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        xmlContext = context
        // Parse minSdkVersion from uses-sdk element
        val root = document.documentElement ?: return
        // Find uses-sdk
        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_USES_SDK) {
                val usesSdk = child as Element
                val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            child = child.nextSibling
        }

        // Find application element and scan its children
        child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "application") {
                val application = child as Element
                scanApplication(application)
            }
            child = child.nextSibling
        }

        // Now report issues
        reportIssues(context)
    }

    private fun scanApplication(application: Element) {
        var child = application.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                when (element.tagName) {
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
            child = child.nextSibling
        }
    }

    private fun reportIssues(context: XmlContext) {
        if (watchFaceEditorServices.isEmpty()) {
            // No watch face service with wearableConfigurationAction metadata; nothing to check
            return
        }

        if (watchFaceEditorActivities.size > 1) {
            // Multiple activities with WATCH_FACE_EDITOR intent filter
            for (activity in watchFaceEditorActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getElementLocation(activity),
                    "Duplicate watch face configuration activities found: there should be " +
                        "exactly one activity with an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        } else if (watchFaceEditorActivities.isEmpty()) {
            // No activity with WATCH_FACE_EDITOR intent filter
            for (service in watchFaceEditorServices) {
                context.report(
                    ISSUE,
                    service,
                    context.getElementLocation(service),
                    "Watch face service defines `wearableConfigurationAction` metadata with " +
                        "value `WATCH_FACE_EDITOR`, but no activity with an intent filter for " +
                        "`$WATCH_FACE_EDITOR_ACTION` was found"
                )
            }
        } else {
            // Exactly one activity — check if it has the required category when minSdkVersion < 30
            if (minSdkVersion < 30) {
                val activity = watchFaceEditorActivities[0]
                if (!activityHasWearableConfigurationCategory(activity)) {
                    context.report(
                        ISSUE,
                        activity,
                        context.getElementLocation(activity),
                        "Watch face configuration activity should include the category " +
                            "`$WEARABLE_CONFIGURATION_CATEGORY` in its intent filter when " +
                            "minSdkVersion is less than 30"
                    )
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? = null

    private fun serviceHasWatchFaceEditorMetadata(service: Element): Boolean {
        var child = service.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_META_DATA) {
                val metaData = child as Element
                val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
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

    private fun activityHasWatchFaceEditorIntentFilter(activity: Element): Boolean {
        var child = activity.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_INTENT_FILTER) {
                val intentFilter = child as Element
                if (intentFilterHasAction(intentFilter, WATCH_FACE_EDITOR_ACTION)) {
                    return true
                }
            }
            child = child.nextSibling
        }
        return false
    }

    private fun activityHasWearableConfigurationCategory(activity: Element): Boolean {
        var child = activity.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_INTENT_FILTER) {
                val intentFilter = child as Element
                if (intentFilterHasAction(intentFilter, WATCH_FACE_EDITOR_ACTION) &&
                    intentFilterHasCategory(intentFilter, WEARABLE_CONFIGURATION_CATEGORY)
                ) {
                    return true
                }
            }
            child = child.nextSibling
        }
        return false
    }

    private fun intentFilterHasAction(intentFilter: Element, action: String): Boolean {
        var child = intentFilter.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_ACTION) {
                val actionElement = child as Element
                val name = actionElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == action) return true
            }
            child = child.nextSibling
        }
        return false
    }

    private fun intentFilterHasCategory(intentFilter: Element, category: String): Boolean {
        var child = intentFilter.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_CATEGORY) {
                val categoryElement = child as Element
                val name = categoryElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == category) return true
            }
            child = child.nextSibling
        }
        return false
    }
}