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
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val META_DATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there must be an activity in the same package that has an \
                intent filter for `WATCH_FACE_EDITOR`. If the `minSdkVersion` is less than 30, this \
                intent filter must also include the `WEARABLE_CONFIGURATION` category.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        val applications = getChildrenByTagName(element, "application")
        val services = applications.flatMap { getChildrenByTagName(it, "service") }
        
        val watchFaceServicesWithConfig = mutableListOf<Element>()
        
        for (service in services) {
            val metaDatas = getChildrenByTagName(service, "meta-data")
            for (metaData in metaDatas) {
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == META_DATA_NAME && value == WATCH_FACE_EDITOR) {
                    watchFaceServicesWithConfig.add(metaData)
                }
            }
        }
        
        if (watchFaceServicesWithConfig.isEmpty()) {
            return
        }
        
        val activities = applications.flatMap { getChildrenByTagName(it, "activity") }
        val minSdkVersion = context.project.minSdkVersion.featureLevel
        
        var hasMatchingActivity = false
        for (activity in activities) {
            val intentFilters = getChildrenByTagName(activity, "intent-filter")
            for (filter in intentFilters) {
                val actions = getChildrenByTagName(filter, "action")
                val hasAction = actions.any { 
                    it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == WATCH_FACE_EDITOR 
                }
                if (hasAction) {
                    if (minSdkVersion >= 30) {
                        hasMatchingActivity = true
                        break
                    } else {
                        val categories = getChildrenByTagName(filter, "category")
                        val hasCategory = categories.any {
                            it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
                        }
                        if (hasCategory) {
                            hasMatchingActivity = true
                            break
                        }
                    }
                }
            }
            if (hasMatchingActivity) {
                break
            }
        }
        
        if (!hasMatchingActivity) {
            for (metaData in watchFaceServicesWithConfig) {
                val message = if (minSdkVersion < 30) {
                    "To support configuration, you must declare an activity with an intent-filter for action `$WATCH_FACE_EDITOR` and category `$WEARABLE_CONFIGURATION_CATEGORY`"
                } else {
                    "To support configuration, you must declare an activity with an intent-filter for action `$WATCH_FACE_EDITOR`"
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
    
    private fun getChildrenByTagName(parent: Element, tagName: String): List<Element> {
        val list = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val name = node.localName ?: node.tagName
                if (name == tagName) {
                    list.add(node)
                }
            }
        }
        return list
    }
}