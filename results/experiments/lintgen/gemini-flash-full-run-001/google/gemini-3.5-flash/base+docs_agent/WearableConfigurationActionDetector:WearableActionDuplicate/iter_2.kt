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
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).
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

    private fun Element.getAndroidAttribute(localName: String): String {
        val nsAttr = getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (nsAttr.isNotEmpty()) return nsAttr
        val attr = getAttribute("android:$localName")
        if (attr.isNotEmpty()) return attr
        return getAttribute(localName)
    }

    private fun isWatchFaceEditor(value: String): Boolean {
        val clean = value.trim()
        return clean == "WATCH_FACE_EDITOR" || 
               clean == "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" ||
               clean.endsWith(".WATCH_FACE_EDITOR")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "manifest") return

        val services = mutableListOf<Element>()
        val serviceNodes = root.getElementsByTagName("service")
        for (i in 0 until serviceNodes.length) {
            services.add(serviceNodes.item(i) as Element)
        }

        val activities = mutableListOf<Element>()
        val activityNodes = root.getElementsByTagName("activity")
        for (i in 0 until activityNodes.length) {
            activities.add(activityNodes.item(i) as Element)
        }
        val aliasNodes = root.getElementsByTagName("activity-alias")
        for (i in 0 until aliasNodes.length) {
            activities.add(aliasNodes.item(i) as Element)
        }

        val servicesWithMetadata = mutableListOf<Element>()
        val metadataElements = mutableListOf<Element>()

        for (service in services) {
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as Element
                val name = metaData.getAndroidAttribute("name")
                val value = metaData.getAndroidAttribute("value")
                if ((name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction") &&
                    isWatchFaceEditor(value)
                ) {
                    servicesWithMetadata.add(service)
                    metadataElements.add(metaData)
                }
            }
        }

        val activitiesWithAction = mutableListOf<Element>()
        val intentFiltersWithAction = mutableListOf<Element>()
        val activitiesWithActionAndCategory = mutableListOf<Element>()

        for (activity in activities) {
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as Element
                val actions = intentFilter.getElementsByTagName("action")
                var hasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAndroidAttribute("name")
                    if (isWatchFaceEditor(actionName)) {
                        hasAction = true
                        break
                    }
                }

                if (hasAction) {
                    activitiesWithAction.add(activity)
                    intentFiltersWithAction.add(intentFilter)

                    val categories = intentFilter.getElementsByTagName("category")
                    var hasCategory = false
                    for (k in 0 until categories.length) {
                        val category = categories.item(k) as Element
                        val categoryName = category.getAndroidAttribute("name")
                        if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                            break
                        }
                    }
                    if (hasCategory) {
                        activitiesWithActionAndCategory.add(activity)
                    }
                }
            }
        }

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        val uniqueActivitiesWithAction = activitiesWithAction.distinct()
        val uniqueActivitiesWithActionAndCategory = activitiesWithActionAndCategory.distinct()

        val hasService = servicesWithMetadata.isNotEmpty()
        val hasActivityWithAction = uniqueActivitiesWithAction.isNotEmpty()
        val hasActivityWithActionAndCategory = uniqueActivitiesWithActionAndCategory.isNotEmpty()

        val satisfiesActivityRequirement = if (minSdkVersion < 30) {
            hasActivityWithActionAndCategory
        } else {
            hasActivityWithAction
        }

        if (uniqueActivitiesWithAction.size > 1) {
            for (activity in uniqueActivitiesWithAction) {
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
        } else {
            if (hasService && !satisfiesActivityRequirement) {
                if (minSdkVersion < 30 && hasActivityWithAction && !hasActivityWithActionAndCategory) {
                    for (intentFilter in intentFiltersWithAction) {
                        context.report(
                            ISSUE,
                            intentFilter,
                            context.getLocation(intentFilter),
                            "Activity with WATCH_FACE_EDITOR action must also include com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category when minSdkVersion < 30"
                        )
                    }
                } else {
                    for (metaData in metadataElements) {
                        context.report(
                            ISSUE,
                            metaData,
                            context.getLocation(metaData),
                            "A watch face service with WATCH_FACE_EDITOR configuration action requires a corresponding activity with WATCH_FACE_EDITOR intent filter"
                        )
                    }
                }
            } else if (!hasService && hasActivityWithAction) {
                for (intentFilter in intentFiltersWithAction) {
                    context.report(
                        ISSUE,
                        intentFilter,
                        context.getLocation(intentFilter),
                        "An activity with WATCH_FACE_EDITOR intent filter requires a corresponding watch face service with com.google.android.wearable.watchface.wearableConfigurationAction metadata"
                    )
                }
            }
        }
    }
}