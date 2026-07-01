package com.android.tools.lint.checks

import com.android.SdkConstants
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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there must be an activity in the same package \
                that has an intent filter for `WATCH_FACE_EDITOR`. If the `minSdkVersion` is \
                less than 30, this intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != SdkConstants.TAG_MANIFEST) return

        val application = root.childElements().firstOrNull { it.tagName == SdkConstants.TAG_APPLICATION } ?: return
        val services = application.childElements().filter { it.tagName == SdkConstants.TAG_SERVICE }
        val activities = application.childElements().filter { it.tagName == SdkConstants.TAG_ACTIVITY }

        val metadataElements = mutableListOf<Element>()
        for (service in services) {
            val metaDatas = service.childElements().filter { it.tagName == SdkConstants.TAG_META_DATA }
            for (metaData in metaDatas) {
                val name = metaData.getAndroidAttribute(SdkConstants.ATTR_NAME)
                val value = metaData.getAndroidAttribute(SdkConstants.ATTR_VALUE)
                val isCorrectName = name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction"
                val isCorrectValue = value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR"
                if (isCorrectName && isCorrectValue) {
                    metadataElements.add(metaData)
                }
            }
        }

        val minSdk = context.project.minSdkVersion.apiLevel
        val matchingIntentFilters = mutableListOf<Element>()

        for (activity in activities) {
            val intentFilters = activity.childElements().filter { it.tagName == SdkConstants.TAG_INTENT_FILTER }
            for (filter in intentFilters) {
                val actions = filter.childElements().filter { it.tagName == SdkConstants.TAG_ACTION }
                val hasAction = actions.any { action ->
                    val name = action.getAndroidAttribute(SdkConstants.ATTR_NAME)
                    name == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || name == "WATCH_FACE_EDITOR"
                }

                if (hasAction) {
                    if (minSdk >= 30) {
                        matchingIntentFilters.add(filter)
                    } else {
                        val categories = filter.childElements().filter { it.tagName == SdkConstants.TAG_CATEGORY }
                        val hasCategory = categories.any { category ->
                            val name = category.getAndroidAttribute(SdkConstants.ATTR_NAME)
                            name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || name == "WEARABLE_CONFIGURATION"
                        }
                        if (hasCategory) {
                            matchingIntentFilters.add(filter)
                        }
                    }
                }
            }
        }

        val hasMetadata = metadataElements.isNotEmpty()
        val hasMatchingActivity = matchingIntentFilters.isNotEmpty()

        if (hasMetadata && !hasMatchingActivity) {
            for (metaData in metadataElements) {
                val message = if (minSdk < 30) {
                    "To support wearable configuration, there must be an activity with an intent filter for action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` (required for minSdkVersion < 30)."
                } else {
                    "To support wearable configuration, there must be an activity with an intent filter for action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR`."
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        } else if (!hasMetadata && hasMatchingActivity) {
            for (filter in matchingIntentFilters) {
                val message = "An activity with intent filter `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` requires a watch face service with `com.google.android.wearable.watchface.wearableConfigurationAction` metadata."
                context.report(
                    ISSUE,
                    filter,
                    context.getLocation(filter),
                    message
                )
            }
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String {
        return getAttributeNS(SdkConstants.ANDROID_URI, localName).takeIf { it.isNotEmpty() }
            ?: getAttribute("android:$localName").takeIf { it.isNotEmpty() }
            ?: getAttribute(localName)
    }

    private fun Element.childElements(): List<Element> {
        val list = mutableListOf<Element>()
        var curr = firstChild
        while (curr != null) {
            if (curr is Element) {
                list.add(curr)
            }
            curr = curr.nextSibling
        }
        return list
    }
}