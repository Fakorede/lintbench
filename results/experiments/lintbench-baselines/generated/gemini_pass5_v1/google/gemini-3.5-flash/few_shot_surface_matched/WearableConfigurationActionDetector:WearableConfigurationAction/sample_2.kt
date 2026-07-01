package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        
        val services = mergedManifest.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metaData = findMetadata(service, "com.google.android.wearable.watchface.wearableConfigurationAction") ?: continue
            val configAction = metaData.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "value") ?: continue
            
            if (configAction != "WATCH_FACE_EDITOR" && 
                configAction != "com.google.android.wearable.watchface.configuration.WATCH_FACE_CONFIG"
            ) {
                continue
            }
            
            val activities = mergedManifest.getElementsByTagName("activity")
            var foundMatchingActivity = false
            var missingCategory = false
            
            val minSdkVersion = context.mainProject.minSdkVersion.apiLevel
            
            for (j in 0 until activities.length) {
                val activity = activities.item(j) as? org.w3c.dom.Element ?: continue
                if (hasIntentFilter(activity, configAction, minSdkVersion < 30) { missingCategory = true }) {
                    foundMatchingActivity = true
                    break
                }
            }
            
            if (!foundMatchingActivity) {
                val location = context.getLocation(metaData)
                val message = if (missingCategory) {
                    "Activity configuration intent filter must include the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` when minSdkVersion < 30"
                } else {
                    "An activity with an intent filter for action `$configAction` must be defined"
                }
                context.report(ISSUE, metaData, location, message)
            }
        }
    }

    private fun findMetadata(service: org.w3c.dom.Element, name: String): org.w3c.dom.Element? {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element && child.tagName == "meta-data") {
                if (child.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name") == name) {
                    return child
                }
            }
        }
        return null
    }

    private fun hasIntentFilter(
        activity: org.w3c.dom.Element, 
        actionName: String, 
        checkCategory: Boolean,
        onMissingCategory: () -> Unit
    ): Boolean {
        val children = activity.childNodes
        var hasAction = false
        var hasCategory = false
        
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element && child.tagName == "intent-filter") {
                val filterChildren = child.childNodes
                var filterHasAction = false
                var filterHasCategory = false
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
                    if (filterChild is org.w3c.dom.Element) {
                        if (filterChild.tagName == "action") {
                            val name = filterChild.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name")
                            if (name == actionName) {
                                filterHasAction = true
                            }
                        } else if (filterChild.tagName == "category") {
                            val name = filterChild.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name")
                            if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                filterHasCategory = true
                            }
                        }
                    }
                }
                if (filterHasAction) {
                    hasAction = true
                    if (filterHasCategory) {
                        hasCategory = true
                    }
                }
            }
        }
        
        if (hasAction) {
            if (checkCategory && !hasCategory) {
                onMissingCategory()
                return false
            }
            return true
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = "When a watch face service defines `wearableConfigurationAction` metadata, " +
                "there should be an activity in the same package which has an intent filter for that " +
                "action (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` " +
                "if `minSdkVersion` is less than 30).",
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