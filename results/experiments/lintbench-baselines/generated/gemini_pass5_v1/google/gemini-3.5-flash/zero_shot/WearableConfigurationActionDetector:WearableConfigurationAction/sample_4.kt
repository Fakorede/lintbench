package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val METADATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val CONFIG_ACTION = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val CONFIG_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata with the value `WATCH_FACE_EDITOR`, \
                there must be an activity in the same package with an intent filter for `WATCH_FACE_EDITOR`. \
                If the `minSdkVersion` is less than 30, this intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private fun isWatchFaceEditor(value: String): Boolean {
            return value == CONFIG_ACTION || value == "WATCH_FACE_EDITOR"
        }
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.file.name != "AndroidManifest.xml") {
            return
        }

        val services = document.getElementsByTagName("service")
        val activities = document.getElementsByTagName("activity")

        val activityList = mutableListOf<Element>()
        for (i in 0 until activities.length) {
            val node = activities.item(i)
            if (node is Element) {
                activityList.add(node)
            }
        }

        for (i in 0 until services.length) {
            val serviceNode = services.item(i) as? Element ?: continue
            val metaDataList = getDirectChildrenByName(serviceNode, "meta-data")

            for (metaData in metaDataList) {
                val name = metaData.getAttributeNS(ANDROID_URI, "name")
                if (name == METADATA_NAME) {
                    val value = metaData.getAttributeNS(ANDROID_URI, "value")
                    if (isWatchFaceEditor(value)) {
                        val minSdkVersion = context.project.minSdkVersion.apiLevel
                        if (!hasMatchingActivity(activityList, value, minSdkVersion)) {
                            val message = if (minSdkVersion < 30) {
                                "To use wearableConfigurationAction, there must be an activity with a matching intent filter for WATCH_FACE_EDITOR and category $CONFIG_CATEGORY"
                            } else {
                                "To use wearableConfigurationAction, there must be an activity with a matching intent filter for WATCH_FACE_EDITOR"
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
            }
        }
    }

    private fun hasMatchingActivity(activities: List<Element>, actionValue: String, minSdkVersion: Int): Boolean {
        for (activity in activities) {
            val intentFilters = getDirectChildrenByName(activity, "intent-filter")
            for (filter in intentFilters) {
                val actions = getDirectChildrenByName(filter, "action")
                val categories = getDirectChildrenByName(filter, "category")

                val hasAction = actions.any {
                    val name = it.getAttributeNS(ANDROID_URI, "name")
                    name == actionValue || (isWatchFaceEditor(name) && isWatchFaceEditor(actionValue))
                }

                if (hasAction) {
                    if (minSdkVersion < 30) {
                        val hasCategory = categories.any {
                            it.getAttributeNS(ANDROID_URI, "name") == CONFIG_CATEGORY
                        }
                        if (hasCategory) {
                            return true
                        }
                    } else {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun getDirectChildrenByName(parent: Node, name: String): List<Element> {
        val list = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.nodeName == name) {
                list.add(child)
            }
        }
        return list
    }
}