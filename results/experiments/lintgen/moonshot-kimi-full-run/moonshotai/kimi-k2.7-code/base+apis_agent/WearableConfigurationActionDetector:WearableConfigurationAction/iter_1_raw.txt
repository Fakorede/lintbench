package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.IdentityHashMap

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private data class IntentFilterInfo(
        var hasEditorAction: Boolean = false,
        var hasWearableConfigurationCategory: Boolean = false
    )

    private data class ActivityInfo(
        val location: Location,
        val filters: MutableList<IntentFilterInfo> = mutableListOf()
    )

    private val serviceMetadataLocations = mutableListOf<Location>()
    private val activityInfos = IdentityHashMap<Element, ActivityInfo>()
    private val intentFilterInfos = IdentityHashMap<Element, IntentFilterInfo>()

    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_MANIFEST,
        TAG_SERVICE,
        TAG_META_DATA,
        TAG_ACTIVITY,
        TAG_INTENT_FILTER,
        TAG_ACTION,
        TAG_CATEGORY
    )

    override fun beforeCheckFile(context: Context) {
        if (context.file.name == ANDROID_MANIFEST_XML) {
            serviceMetadataLocations.clear()
            activityInfos.clear()
            intentFilterInfos.clear()
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.file.name != ANDROID_MANIFEST_XML) {
            return
        }

        when (localName(element)) {
            TAG_META_DATA -> {
                val parent = element.parentNode
                if (parent is Element && localName(parent) == TAG_SERVICE) {
                    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR_VALUE) {
                        serviceMetadataLocations.add(context.getLocation(element))
                    }
                }
            }
            TAG_ACTIVITY -> {
                activityInfos[element] = ActivityInfo(context.getLocation(element))
            }
            TAG_INTENT_FILTER -> {
                val activity = findEnclosingActivity(element)
                if (activity != null) {
                    val filter = IntentFilterInfo()
                    activity.filters.add(filter)
                    intentFilterInfos[element] = filter
                }
            }
            TAG_ACTION -> {
                val actionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == ACTION_WATCH_FACE_EDITOR) {
                    findEnclosingIntentFilter(element)?.hasEditorAction = true
                }
            }
            TAG_CATEGORY -> {
                val categoryName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (categoryName == CATEGORY_WEARABLE_CONFIGURATION) {
                    findEnclosingIntentFilter(element)?.hasWearableConfigurationCategory = true
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context.file.name != ANDROID_MANIFEST_XML || serviceMetadataLocations.isEmpty()) {
            return
        }

        val requiresCategory = context.project.minSdk < 30

        val hasMatchingActivity = activityInfos.values.any { activity ->
            activity.filters.any { filter ->
                filter.hasEditorAction && (!requiresCategory || filter.hasWearableConfigurationCategory)
            }
        }

        if (!hasMatchingActivity) {
            val message = buildString {
                append("This watch face service declares wearableConfigurationAction=WATCH_FACE_EDITOR, ")
                append("but no activity in the same package has an intent filter for ")
                append(ACTION_WATCH_FACE_EDITOR)
                if (requiresCategory) {
                    append(" with category ")
                    append(CATEGORY_WEARABLE_CONFIGURATION)
                }
                append(".")
            }

            for (location in serviceMetadataLocations) {
                context.report(ISSUE, location, message)
            }
        }

        serviceMetadataLocations.clear()
        activityInfos.clear()
        intentFilterInfos.clear()
    }

    private fun findEnclosingActivity(node: Node): ActivityInfo? {
        var current = node.parentNode
        while (current != null) {
            if (current is Element && localName(current) == TAG_ACTIVITY) {
                return activityInfos[current]
            }
            current = current.parentNode
        }
        return null
    }

    private fun findEnclosingIntentFilter(node: Node): IntentFilterInfo? {
        var current = node.parentNode
        while (current != null) {
            if (current is Element && localName(current) == TAG_INTENT_FILTER) {
                return intentFilterInfos[current]
            }
            current = current.parentNode
        }
        return null
    }

    private fun localName(element: Element): String = element.localName ?: element.nodeName

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_VALUE = "WATCH_FACE_EDITOR"
        private const val ACTION_WATCH_FACE_EDITOR =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action must match an activity",
            explanation = """
                When a watch face service declares the metadata
                `com.google.android.wearable.watchface.wearableConfigurationAction`
                with the value `WATCH_FACE_EDITOR`, there must be a configuration activity
                in the same package with an intent filter for
                `com.google.android.wearable.watchface.WATCH_FACE_EDITOR`.
                If `minSdkVersion` is less than 30, that intent filter must also include the
                category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}