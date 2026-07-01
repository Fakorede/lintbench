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

    override fun beforeCheckFile(context: Context) {
        minSdkVersion = 1
        watchFaceEditorServices.clear()
        watchFaceEditorActivities.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Find uses-sdk for minSdkVersion
        forEachChildElement(root) { child ->
            if (child.nodeName == TAG_USES_SDK) {
                val minSdk = child.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
        }

        // Find application element and scan its children
        forEachChildElement(root) { child ->
            if (child.nodeName == "application") {
                scanApplication(child)
            }
        }

        // Now report issues
        reportIssues(context)
    }

    private fun scanApplication(application: Element) {
        forEachChildElement(application) { element ->
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
    }

    private fun reportIssues(context: XmlContext) {
        if (watchFaceEditorServices.isEmpty()) {
            // No watch face service with wearableConfigurationAction metadata; nothing to check
            return
        }

        if (watchFaceEditorActivities.size > 1) {
            // Multiple activities with WATCH_FACE_EDITOR intent filter - report all duplicates
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
            // No activity with WATCH_FACE_EDITOR intent filter - report on each service
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

    private fun forEachChildElement(parent: Element, action: (Element) -> Unit) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                action(child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun serviceHasWatchFaceEditorMetadata(service: Element): Boolean {
        var found = false
        forEachChildElement(service) { child ->
            if (!found && child.nodeName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    found = true
                }
            }
        }
        return found
    }

    private fun activityHasWatchFaceEditorIntentFilter(activity: Element): Boolean {
        var found = false
        forEachChildElement(activity) { child ->
            if (!found && child.nodeName == TAG_INTENT_FILTER) {
                if (intentFilterHasAction(child, WATCH_FACE_EDITOR_ACTION)) {
                    found = true
                }
            }
        }
        return found
    }

    private fun activityHasWearableConfigurationCategory(activity: Element): Boolean {
        var found = false
        forEachChildElement(activity) { child ->
            if (!found && child.nodeName == TAG_INTENT_FILTER) {
                if (intentFilterHasAction(child, WATCH_FACE_EDITOR_ACTION) &&
                    intentFilterHasCategory(child, WEARABLE_CONFIGURATION_CATEGORY)
                ) {
                    found = true
                }
            }
        }
        return found
    }

    private fun intentFilterHasAction(intentFilter: Element, action: String): Boolean {
        var found = false
        forEachChildElement(intentFilter) { child ->
            if (!found && child.nodeName == TAG_ACTION) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == action) {
                    found = true
                }
            }
        }
        return found
    }

    private fun intentFilterHasCategory(intentFilter: Element, category: String): Boolean {
        var found = false
        forEachChildElement(intentFilter) { child ->
            if (!found && child.nodeName == TAG_CATEGORY) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == category) {
                    found = true
                }
            }
        }
        return found
    }
}