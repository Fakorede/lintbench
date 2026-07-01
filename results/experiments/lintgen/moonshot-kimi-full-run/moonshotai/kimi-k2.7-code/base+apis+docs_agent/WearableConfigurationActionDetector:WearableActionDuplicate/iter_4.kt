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
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_MANIFEST) return

        val packageName = element.getAttribute(ATTR_PACKAGE)
        if (packageName.isEmpty()) return

        val minSdk = context.mainProject.minSdk

        val services = mutableListOf<Element>()
        val serviceNodes = element.getElementsByTagName(TAG_SERVICE)
        for (i in 0 until serviceNodes.length) {
            val service = serviceNodes.item(i) as? Element ?: continue
            service.findWearableConfigurationMetadata()?.let { services.add(it) }
        }

        if (services.isEmpty()) return

        val activities = mutableListOf<Element>()
        val activityNodes = element.getElementsByTagName(TAG_ACTIVITY)
        for (i in 0 until activityNodes.length) {
            val activity = activityNodes.item(i) as? Element ?: continue
            if (activity.isConfigurationActivity(packageName, minSdk)) {
                activities.add(activity)
            }
        }
        val aliasNodes = element.getElementsByTagName(TAG_ACTIVITY_ALIAS)
        for (i in 0 until aliasNodes.length) {
            val alias = aliasNodes.item(i) as? Element ?: continue
            if (alias.isConfigurationActivity(packageName, minSdk)) {
                activities.add(alias)
            }
        }

        if (services.size > 1) {
            services.forEach { metadata ->
                context.report(
                    ISSUE,
                    metadata,
                    context.getLocation(metadata),
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        when {
            activities.isEmpty() -> {
                services.forEach { metadata ->
                    context.report(
                        ISSUE,
                        metadata,
                        context.getLocation(metadata),
                        "No configuration activity found for watch face with wearableConfigurationAction WATCH_FACE_EDITOR"
                    )
                }
            }
            activities.size > 1 -> {
                activities.forEach { activity ->
                    context.report(
                        ISSUE,
                        activity,
                        context.getLocation(activity),
                        "Duplicate watch face configuration activities found"
                    )
                }
            }
        }
    }

    private fun Element.findWearableConfigurationMetadata(): Element? {
        val metadataNodes = getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metadataNodes.length) {
            val metadata = metadataNodes.item(i) as? Element ?: continue
            val name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                return metadata
            }
        }
        return null
    }

    private fun Element.isConfigurationActivity(packageName: String, minSdk: Int): Boolean {
        val activityName = getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (activityName.isEmpty()) return false
        if (!isInPackage(activityName, packageName)) return false

        val filters = getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue
            if (filter.hasWatchFaceEditorFilter(minSdk)) {
                return true
            }
        }
        return false
    }

    private fun Element.hasWatchFaceEditorFilter(minSdk: Int): Boolean {
        val actionNodes = getElementsByTagName(TAG_ACTION)
        val hasAction = (0 until actionNodes.length).any { index ->
            val action = actionNodes.item(index) as? Element ?: return@any false
            action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR
        }
        if (!hasAction) return false

        if (minSdk >= 30) return true

        val categoryNodes = getElementsByTagName(TAG_CATEGORY)
        return (0 until categoryNodes.length).any { index ->
            val category = categoryNodes.item(index) as? Element ?: return@any false
            category.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
        }
    }

    private fun isInPackage(name: String, packageName: String): Boolean = when {
        name.startsWith(".") -> true
        !name.contains(".") -> true
        name.startsWith("$packageName.") -> true
        else -> false
    }

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
                Having multiple services or activities that match the same watch face \
                configuration action is an error.
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