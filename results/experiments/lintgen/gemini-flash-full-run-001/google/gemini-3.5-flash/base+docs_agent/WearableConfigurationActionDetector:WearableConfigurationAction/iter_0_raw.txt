package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be an activity in the same \
                package that has an intent filter for `WATCH_FACE_EDITOR`. If the `minSdkVersion` \
                is less than 30, this intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val META_DATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val META_DATA_VALUE = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val ACTION_NAME = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.file.name != SdkConstants.FN_ANDROID_MANIFEST_XML) {
            return
        }

        val root = document.documentElement ?: return
        val application = getFirstChildElementByName(root, SdkConstants.TAG_APPLICATION) ?: return

        val servicesWithMetadata = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        var child = application.firstChild
        while (child != null) {
            if (child is Element) {
                when (child.tagName) {
                    SdkConstants.TAG_SERVICE -> {
                        val metaDataElement = findMetadataElement(child)
                        if (metaDataElement != null) {
                            servicesWithMetadata.add(metaDataElement)
                        }
                    }
                    SdkConstants.TAG_ACTIVITY -> {
                        activities.add(child)
                    }
                }
            }
            child = child.nextSibling
        }

        if (servicesWithMetadata.isEmpty()) {
            return
        }

        val minSdk = context.project.minSdkVersion.apiLevel
        val needsCategory = minSdk < 30

        var hasMatchingActivity = false
        for (activity in activities) {
            if (matchesConfigurationActivity(activity, needsCategory)) {
                hasMatchingActivity = true
                break
            }
        }

        if (!hasMatchingActivity) {
            for (metaData in servicesWithMetadata) {
                val message = if (needsCategory) {
                    "To use WATCH_FACE_EDITOR configuration, there must be an activity with an intent-filter for action `$ACTION_NAME` and category `$CATEGORY_NAME` (required for minSdkVersion < 30)."
                } else {
                    "To use WATCH_FACE_EDITOR configuration, there must be an activity with an intent-filter for action `$ACTION_NAME`."
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        }
    }

    private fun getFirstChildElementByName(parent: Element, name: String): Element? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && child.tagName == name) {
                return child
            }
            child = child.nextSibling
        }
        return null
    }

    private fun findMetadataElement(service: Element): Element? {
        var child = service.firstChild
        while (child != null) {
            if (child is Element && child.tagName == SdkConstants.TAG_META_DATA) {
                val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == META_DATA_NAME && value == META_DATA_VALUE) {
                    return child
                }
            }
            child = child.nextSibling
        }
        return null
    }

    private fun matchesConfigurationActivity(activity: Element, needsCategory: Boolean): Boolean {
        var child = activity.firstChild
        while (child != null) {
            if (child is Element && child.tagName == SdkConstants.TAG_INTENT_FILTER) {
                if (intentFilterMatches(child, needsCategory)) {
                    return true
                }
            }
            child = child.nextSibling
        }
        return false
    }

    private fun intentFilterMatches(intentFilter: Element, needsCategory: Boolean): Boolean {
        var hasAction = false
        var hasCategory = false

        var child = intentFilter.firstChild
        while (child != null) {
            if (child is Element) {
                when (child.tagName) {
                    SdkConstants.TAG_ACTION -> {
                        val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (name == ACTION_NAME) {
                            hasAction = true
                        }
                    }
                    SdkConstants.TAG_CATEGORY -> {
                        val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (name == CATEGORY_NAME) {
                            hasCategory = true
                        }
                    }
                }
            }
            child = child.nextSibling
        }

        return if (needsCategory) {
            hasAction && hasCategory
        } else {
            hasAction
        }
    }
}