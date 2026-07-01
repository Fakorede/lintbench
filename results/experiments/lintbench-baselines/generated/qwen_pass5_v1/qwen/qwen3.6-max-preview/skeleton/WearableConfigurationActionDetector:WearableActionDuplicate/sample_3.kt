package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata, " +
                "there should be exactly one activity in the same package with a matching intent filter. " +
                "Multiple matching activities were found, which can lead to unexpected behavior when " +
                "configuring the watch face.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val META_DATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val CATEGORY_WEARABLE_CONFIG = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun checkMergedProject(context: Context) {
        val manifest = context.mergedManifest ?: return
        val documentElement = manifest.documentElement ?: return

        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1

        val requiredActions = mutableSetOf<String>()
        val services = documentElement.getElementsByTagName(SdkConstants.TAG_SERVICE)
        iterateNodeList(services) { service ->
            val metaDatas = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
            iterateNodeList(metaDatas) { metaData ->
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == META_DATA_NAME) {
                    val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                    if (value.isNotEmpty()) {
                        requiredActions.add(value)
                    }
                }
            }
        }

        if (requiredActions.isEmpty()) return

        val activitiesByAction = mutableMapOf<String, MutableList<Element>>()
        val activities = documentElement.getElementsByTagName(SdkConstants.TAG_ACTIVITY)
        iterateNodeList(activities) { activity ->
            val intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
            iterateNodeList(intentFilters) { intentFilter ->
                val actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION)
                iterateNodeList(actions) { action ->
                    val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (actionName in requiredActions) {
                        val isValidMatch = if (minSdk < 30) {
                            val categories = intentFilter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
                            var hasRequiredCategory = false
                            iterateNodeList(categories) { cat ->
                                val catName = cat.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                                if (catName == CATEGORY_WEARABLE_CONFIG) {
                                    hasRequiredCategory = true
                                }
                            }
                            hasRequiredCategory
                        } else {
                            true
                        }

                        if (isValidMatch) {
                            activitiesByAction.getOrPut(actionName) { mutableListOf() }.add(activity)
                        }
                    }
                }
            }
        }

        for ((action, activityList) in activitiesByAction) {
            if (activityList.size > 1) {
                for (activity in activityList) {
                    context.report(
                        ISSUE,
                        context.getLocation(activity),
                        "Duplicate configuration activity found for action `$action`"
                    )
                }
            }
        }
    }

    private fun iterateNodeList(nodeList: NodeList, action: (Element) -> Unit) {
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element) {
                action(node)
            }
        }
    }
}