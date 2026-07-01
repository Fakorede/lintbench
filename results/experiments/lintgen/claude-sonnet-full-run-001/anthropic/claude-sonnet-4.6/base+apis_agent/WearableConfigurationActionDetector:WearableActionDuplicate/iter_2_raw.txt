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

    // Per-manifest state
    private var minSdkVersion: Int = 1
    private var watchFaceEditorServices: MutableList<Element> = mutableListOf()
    private var watchFaceEditorActivities: MutableList<Element> = mutableListOf()
    private var savedContext: XmlContext? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_USES_SDK,
            TAG_SERVICE,
            TAG_ACTIVITY
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        savedContext = context
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            TAG_SERVICE -> {
                if (hasWatchFaceEditorMetadataInService(element)) {
                    watchFaceEditorServices.add(element)
                }
            }
            TAG_ACTIVITY -> {
                if (activityHasWatchFaceEditorAction(element)) {
                    watchFaceEditorActivities.add(element)
                }
            }
        }
    }

    private fun hasWatchFaceEditorMetadataInService(serviceElement: Element): Boolean {
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
                    if (intentFilterHasWatchFaceEditorAction(childElement)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun intentFilterHasWatchFaceEditorAction(intentFilterElement: Element): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "action") {
                    val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WATCH_FACE_EDITOR_ACTION) {
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
                    if (intentFilterHasWearableConfigurationCategory(childElement)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun intentFilterHasWearableConfigurationCategory(intentFilterElement: Element): Boolean {
        val children = intentFilterElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "category") {
                    val name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun afterCheckFile(context: Context) {
        val ctx = savedContext ?: run { reset(); return }

        // Only proceed if there are services with the watch face editor metadata
        if (watchFaceEditorServices.isEmpty()) {
            reset()
            return
        }

        // Check for duplicate activities (more than one activity with WATCH_FACE_EDITOR action)
        if (watchFaceEditorActivities.size > 1) {
            for (activity in watchFaceEditorActivities) {
                ctx.report(
                    ISSUE,
                    activity,
                    ctx.getNameLocation(activity),
                    "Duplicate watch face configuration activities found: multiple activities " +
                            "declare an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        } else if (watchFaceEditorActivities.size == 1) {
            // Exactly one activity - check if category is required
            if (minSdkVersion < 30) {
                val activity = watchFaceEditorActivities[0]
                if (!activityHasWearableConfigurationCategory(activity)) {
                    ctx.report(
                        ISSUE,
                        activity,
                        ctx.getNameLocation(activity),
                        "Watch face configuration activity should include " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` category in its intent " +
                                "filter when minSdkVersion is less than 30"
                    )
                }
            }
        } else {
            // No activity found but service has metadata
            for (service in watchFaceEditorServices) {
                ctx.report(
                    ISSUE,
                    service,
                    ctx.getNameLocation(service),
                    "Watch face service defines `wearableConfigurationAction` metadata with " +
                            "value `$WATCH_FACE_EDITOR_ACTION` but no activity with a " +
                            "corresponding intent filter was found"
                )
            }
        }

        reset()
    }

    private fun reset() {
        minSdkVersion = 1
        watchFaceEditorServices = mutableListOf()
        watchFaceEditorActivities = mutableListOf()
        savedContext = null
    }
}