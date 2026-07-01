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

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same \
                package which has an intent filter for `WATCH_FACE_EDITOR` (and \
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Find minSdkVersion
        var minSdkVersion = 1
        val usesSdkList = root.getElementsByTagName(TAG_USES_SDK)
        for (i in 0 until usesSdkList.length) {
            val usesSdk = usesSdkList.item(i) as? Element ?: continue
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                minSdkVersion = minSdk.toIntOrNull() ?: 1
            }
        }

        // Find all services with wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val serviceList = root.getElementsByTagName(TAG_SERVICE)
        val watchFaceEditorServices = mutableListOf<Element>()
        for (i in 0 until serviceList.length) {
            val service = serviceList.item(i) as? Element ?: continue
            if (hasWatchFaceEditorMetadata(service)) {
                watchFaceEditorServices.add(service)
            }
        }

        if (watchFaceEditorServices.isEmpty()) return

        // Find all activities with WATCH_FACE_EDITOR action intent filter
        val activityList = root.getElementsByTagName(TAG_ACTIVITY)
        val watchFaceEditorActivities = mutableListOf<Element>()
        for (i in 0 until activityList.length) {
            val activity = activityList.item(i) as? Element ?: continue
            if (activityHasWatchFaceEditorAction(activity)) {
                watchFaceEditorActivities.add(activity)
            }
        }

        // Check for duplicate activities (more than one activity with WATCH_FACE_EDITOR action)
        if (watchFaceEditorActivities.size > 1) {
            for (activity in watchFaceEditorActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found: multiple activities " +
                            "declare an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        } else if (watchFaceEditorActivities.size == 1) {
            // Exactly one activity - check if category is required
            if (minSdkVersion < 30) {
                val activity = watchFaceEditorActivities[0]
                if (!activityHasWearableConfigurationCategory(activity)) {
                    context.report(
                        ISSUE,
                        activity,
                        context.getNameLocation(activity),
                        "Watch face configuration activity should include " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` category in its intent " +
                                "filter when minSdkVersion is less than 30"
                    )
                }
            }
        } else {
            // No activity found but service has metadata
            for (service in watchFaceEditorServices) {
                context.report(
                    ISSUE,
                    service,
                    context.getNameLocation(service),
                    "Watch face service defines `wearableConfigurationAction` metadata with " +
                            "value `$WATCH_FACE_EDITOR_ACTION` but no activity with a " +
                            "corresponding intent filter was found"
                )
            }
        }
    }

    private fun hasWatchFaceEditorMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "meta-data") {
                    val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val value = childElement.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                        value == WATCH_FACE_EDITOR_ACTION
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun activityHasWatchFaceEditorAction(activityElement: Element): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == TAG_INTENT_FILTER) {
                    if (intentFilterHasAction(childElement, WATCH_FACE_EDITOR_ACTION)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun intentFilterHasAction(intentFilterElement: Element, action: String): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "action") {
                    val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == action) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun activityHasWearableConfigurationCategory(activityElement: Element): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == TAG_INTENT_FILTER) {
                    if (intentFilterHasCategory(childElement, WEARABLE_CONFIGURATION_CATEGORY)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun intentFilterHasCategory(intentFilterElement: Element, category: String): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "category") {
                    val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == category) {
                        return true
                    }
                }
            }
        }
        return false
    }
}