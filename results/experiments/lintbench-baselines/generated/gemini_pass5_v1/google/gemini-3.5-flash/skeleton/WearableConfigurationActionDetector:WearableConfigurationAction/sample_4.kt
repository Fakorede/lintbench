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

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines the wearableConfigurationAction metadata, there must be a corresponding activity in the same package that has an intent filter for that action. If the minSdkVersion is less than 30, this intent filter must also include the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val applications = getChildrenByTagName(root, "application")
        val allActivities = applications.flatMap { getChildrenByTagName(it, "activity") }
        val allServices = applications.flatMap { getChildrenByTagName(it, "service") }

        for (service in allServices) {
            val metaDataList = getChildrenByTagName(service, "meta-data")
            var configAction: String? = null
            var metaDataElement: org.w3c.dom.Element? = null

            for (metaData in metaDataList) {
                val name = getAndroidAttribute(metaData, "name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    configAction = getAndroidAttribute(metaData, "value")
                    metaDataElement = metaData
                    break
                }
            }

            if (!configAction.isNullOrEmpty()) {
                val minSdkVersion = context.mainProject.minSdkVersion.featureLevel
                val requiresCategory = minSdkVersion < 30

                var foundMatchingActivity = false

                for (activity in allActivities) {
                    val intentFilters = getChildrenByTagName(activity, "intent-filter")
                    for (filter in intentFilters) {
                        val actions = getChildrenByTagName(filter, "action")
                        val hasMatchingAction = actions.any { getAndroidAttribute(it, "name") == configAction }

                        if (hasMatchingAction) {
                            if (requiresCategory) {
                                val categories = getChildrenByTagName(filter, "category")
                                val hasMatchingCategory = categories.any {
                                    getAndroidAttribute(it, "name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
                                }
                                if (hasMatchingCategory) {
                                    foundMatchingActivity = true
                                    break
                                }
                            } else {
                                foundMatchingActivity = true
                                break
                            }
                        }
                    }
                    if (foundMatchingActivity) {
                        break
                    }
                }

                if (!foundMatchingActivity) {
                    val location = context.getLocation(metaDataElement!!)
                    val message = if (requiresCategory) {
                        "An activity with an intent filter for action \"$configAction\" and category \"com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION\" must be defined."
                    } else {
                        "An activity with an intent filter for action \"$configAction\" must be defined."
                    }
                    context.report(ISSUE, location, message)
                }
            }
        }
    }

    private fun getChildrenByTagName(parent: org.w3c.dom.Element, tagName: String): List<org.w3c.dom.Element> {
        val list = mutableListOf<org.w3c.dom.Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element && child.tagName == tagName) {
                list.add(child)
            }
        }
        return list
    }

    private fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
        return element.getAttributeNS("http://schemas.android.com/apk/res/android", localName).takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:$localName")
    }
}