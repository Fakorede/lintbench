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
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val METADATA_WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package, which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                `minSdkVersion` is less than 30).
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration",
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )

        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
        private const val TAG_META_DATA = "meta-data"
    }

    // Per-file state
    private var minSdkVersion: Int = 1

    // Services that have the wearableConfigurationAction metadata with WATCH_FACE_EDITOR value
    private data class ServiceMetadata(val element: Element, val context: XmlContext)

    private val servicesWithMetadata = mutableListOf<ServiceMetadata>()

    // Whether there is an activity with WATCH_FACE_EDITOR intent filter
    private var activityWithEditorAction = false
    // Whether there is an activity with WATCH_FACE_EDITOR + WEARABLE_CONFIGURATION_CATEGORY
    private var activityWithEditorActionAndCategory = false

    override fun beforeCheckFile(context: Context) {
        minSdkVersion = 1
        servicesWithMetadata.clear()
        activityWithEditorAction = false
        activityWithEditorActionAndCategory = false
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_USES_SDK,
            TAG_SERVICE,
            TAG_ACTIVITY
        )
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
                checkService(context, element)
            }
            TAG_ACTIVITY -> {
                checkActivity(context, element)
            }
        }
    }

    private fun checkService(context: XmlContext, serviceElement: Element) {
        // Look for meta-data children with the wearableConfigurationAction name
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == METADATA_WEARABLE_CONFIGURATION_ACTION &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    servicesWithMetadata.add(ServiceMetadata(child, context))
                }
            }
        }
    }

    private fun checkActivity(context: XmlContext, activityElement: Element) {
        // Look for intent-filter children
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                val (hasAction, hasCategory) = checkIntentFilter(child)
                if (hasAction) {
                    activityWithEditorAction = true
                    if (hasCategory) {
                        activityWithEditorActionAndCategory = true
                    }
                }
            }
        }
    }

    private fun checkIntentFilter(intentFilter: Element): Pair<Boolean, Boolean> {
        var hasEditorAction = false
        var hasWearableCategory = false

        val children = intentFilter.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            when (child.tagName) {
                TAG_ACTION -> {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WATCH_FACE_EDITOR_ACTION) {
                        hasEditorAction = true
                    }
                }
                TAG_CATEGORY -> {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                        hasWearableCategory = true
                    }
                }
            }
        }

        return Pair(hasEditorAction, hasWearableCategory)
    }

    override fun afterCheckFile(context: Context) {
        if (servicesWithMetadata.isEmpty()) {
            return
        }

        val requiresCategory = minSdkVersion < 30

        for ((element, xmlContext) in servicesWithMetadata) {
            if (requiresCategory) {
                if (!activityWithEditorActionAndCategory) {
                    xmlContext.report(
                        ISSUE,
                        element,
                        xmlContext.getLocation(element),
                        "This service defines `wearableConfigurationAction` metadata with " +
                            "`WATCH_FACE_EDITOR`, but no activity in this package has an " +
                            "intent filter for `$WATCH_FACE_EDITOR_ACTION` with category " +
                            "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                    )
                }
            } else {
                if (!activityWithEditorAction) {
                    xmlContext.report(
                        ISSUE,
                        element,
                        xmlContext.getLocation(element),
                        "This service defines `wearableConfigurationAction` metadata with " +
                            "`WATCH_FACE_EDITOR`, but no activity in this package has an " +
                            "intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                    )
                }
            }
        }
    }
}