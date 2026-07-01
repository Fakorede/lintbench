package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val services = mutableListOf<Element>()
    private val activities = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_SERVICE,
        TAG_ACTIVITY,
        TAG_ACTIVITY_ALIAS
    )

    override fun beforeCheckFile(context: Context) {
        services.clear()
        activities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                val metadata = element.findWearableConfigurationMetadata()
                if (metadata != null) {
                    services.add(metadata)
                }
            }
            TAG_ACTIVITY, TAG_ACTIVITY_ALIAS -> {
                val info = element.toActivityInfo()
                if (info != null) {
                    activities.add(info)
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (services.isEmpty()) return

        val xmlContext = context as XmlContext
        val manifest = xmlContext.document.documentElement
        val packageName = manifest.getAttribute(ATTR_PACKAGE)
        if (packageName.isEmpty()) return

        val minSdk = xmlContext.project.minSdk
        val matchingActivities = activities.filter { activity ->
            activity.isInPackage(packageName) && activity.hasWatchFaceEditorFilter(minSdk)
        }

        when {
            matchingActivities.isEmpty() -> {
                services.forEach { metadata ->
                    xmlContext.report(
                        ISSUE,
                        metadata,
                        xmlContext.getLocation(metadata),
                        "No configuration activity found for watch face with wearableConfigurationAction WATCH_FACE_EDITOR"
                    )
                }
            }
            matchingActivities.size > 1 -> {
                matchingActivities.forEach { activity ->
                    xmlContext.report(
                        ISSUE,
                        activity.element,
                        xmlContext.getLocation(activity.element),
                        "Duplicate watch face configuration activities found"
                    )
                }
            }
        }
    }

    private fun Element.findWearableConfigurationMetadata(): Element? {
        val metadata = getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metadata.length) {
            val meta = metadata.item(i) as? Element ?: continue
            val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                return meta
            }
        }
        return null
    }

    private fun Element.toActivityInfo(): ActivityInfo? {
        val name = getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotEmpty() } ?: return null
        val filters = mutableListOf<IntentFilterInfo>()
        val intentFilters = getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as? Element ?: continue
            val hasAction = filter.getElementsByTagName(TAG_ACTION).containsName(WATCH_FACE_EDITOR)
            val hasCategory = filter.getElementsByTagName(TAG_CATEGORY)
                .containsName(WEARABLE_CONFIGURATION_CATEGORY)
            if (hasAction) {
                filters.add(IntentFilterInfo(hasAction = true, hasCategory = hasCategory))
            }
        }
        return if (filters.isNotEmpty()) ActivityInfo(this, name, filters) else null
    }

    private fun NodeList.containsName(name: String): Boolean {
        for (i in 0 until length) {
            val element = item(i) as? Element ?: continue
            if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) == name) {
                return true
            }
        }
        return false
    }

    private fun ActivityInfo.isInPackage(packageName: String): Boolean = when {
        name.startsWith('.') -> true
        name.startsWith("$packageName.") -> true
        !name.contains('.') -> true
        else -> false
    }

    private fun ActivityInfo.hasWatchFaceEditorFilter(minSdk: Int): Boolean =
        intentFilters.any { it.hasAction && (minSdk >= 30 || it.hasCategory) }

    private data class ActivityInfo(
        val element: Element,
        val name: String,
        val intentFilters: List<IntentFilterInfo>
    )

    private data class IntentFilterInfo(
        val hasAction: Boolean,
        val hasCategory: Boolean
    )

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be an activity in the same \
                package with an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` \
                is less than 30, the intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`. \
                Having multiple activities that match the same watch face configuration \
                action is an error.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}