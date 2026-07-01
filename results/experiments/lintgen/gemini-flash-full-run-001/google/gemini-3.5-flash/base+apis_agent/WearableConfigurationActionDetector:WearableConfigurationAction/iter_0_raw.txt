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
        
        val watchFaceServices = mutableListOf<Element>()
        
        for (service in services) {
            val metaDatas = service.childElements().filter { it.tagName == SdkConstants.TAG_META_DATA }
            for (metaData in metaDatas) {
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                    value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                    watchFaceServices.add(metaData)
                }
            }
        }
        
        if (watchFaceServices.isEmpty()) return
        
        val minSdk = context.project.minSdkVersion.apiLevel
        val activities = application.childElements().filter { it.tagName == SdkConstants.TAG_ACTIVITY }
        
        var hasMatchingActivity = false
        
        for (activity in activities) {
            val intentFilters = activity.childElements().filter { it.tagName == SdkConstants.TAG_INTENT_FILTER }
            for (filter in intentFilters) {
                val actions = filter.childElements().filter { it.tagName == SdkConstants.TAG_ACTION }
                val hasAction = actions.any { 
                    it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == 
                        "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" 
                }
                
                if (hasAction) {
                    if (minSdk >= 30) {
                        hasMatchingActivity = true
                        break
                    } else {
                        val categories = filter.childElements().filter { it.tagName == SdkConstants.TAG_CATEGORY }
                        val hasCategory = categories.any { 
                            it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == 
                                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" 
                        }
                        if (hasCategory) {
                            hasMatchingActivity = true
                            break
                        }
                    }
                }
            }
            if (hasMatchingActivity) break
        }
        
        if (!hasMatchingActivity) {
            for (metaData in watchFaceServices) {
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
        }
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