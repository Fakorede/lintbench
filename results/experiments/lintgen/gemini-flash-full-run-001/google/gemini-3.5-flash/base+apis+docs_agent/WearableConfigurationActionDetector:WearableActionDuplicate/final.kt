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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "manifest") return

        val servicesList = mutableListOf<Element>()
        val servicesNodeList = document.getElementsByTagName("service")
        for (i in 0 until servicesNodeList.length) {
            val node = servicesNodeList.item(i)
            if (node is Element) {
                servicesList.add(node)
            }
        }

        val activitiesList = mutableListOf<Element>()
        val activitiesNodeList = document.getElementsByTagName("activity")
        for (i in 0 until activitiesNodeList.length) {
            val node = activitiesNodeList.item(i)
            if (node is Element) {
                activitiesList.add(node)
            }
        }

        val serviceActionToServices = mutableMapOf<String, MutableList<Element>>()

        for (service in servicesList) {
            val childNodes = service.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element && child.tagName == "meta-data") {
                    val name = child.getAndroidAttribute("name")
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                        name == "android.support.wearable.watchface.wearableConfigurationAction") {
                        val value = child.getAndroidAttribute("value")
                        if (!value.isNullOrEmpty()) {
                            serviceActionToServices.getOrPut(value) { mutableListOf() }.add(service)
                        }
                    }
                }
            }
        }

        class ActivityInfo(val element: Element, val actions: Set<String>, val categories: Set<String>)
        val activityInfos = mutableListOf<ActivityInfo>()

        for (activity in activitiesList) {
            val actions = mutableSetOf<String>()
            val categories = mutableSetOf<String>()
            val childNodes = activity.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element && child.tagName == "intent-filter") {
                    val filterChildren = child.childNodes
                    for (j in 0 until filterChildren.length) {
                        val filterChild = filterChildren.item(j)
                        if (filterChild is Element) {
                            if (filterChild.tagName == "action") {
                                val actionName = filterChild.getAndroidAttribute("name")
                                if (!actionName.isNullOrEmpty()) {
                                    actions.add(actionName)
                                }
                            } else if (filterChild.tagName == "category") {
                                val categoryName = filterChild.getAndroidAttribute("name")
                                if (!categoryName.isNullOrEmpty()) {
                                    categories.add(categoryName)
                                }
                            }
                        }
                    }
                }
            }
            activityInfos.add(ActivityInfo(activity, actions, categories))
        }

        val targetActions = mutableSetOf<String>()
        targetActions.addAll(serviceActionToServices.keys)

        for (activityInfo in activityInfos) {
            if (activityInfo.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") ||
                activityInfo.categories.contains("android.support.wearable.watchface.category.WEARABLE_CONFIGURATION")) {
                targetActions.addAll(activityInfo.actions)
            }
            for (action in activityInfo.actions) {
                if (action.contains("WATCH_FACE_EDITOR")) {
                    targetActions.add(action)
                }
            }
        }

        val minSdkVersion = context.project.minSdkVersion.featureLevel
        val reported = mutableSetOf<String>()

        fun report(el: Element, message: String) {
            val key = "${el.hashCode()}:$message"
            if (reported.add(key)) {
                context.report(
                    ISSUE,
                    el,
                    context.getNameLocation(el),
                    message
                )
            }
        }

        for (action in targetActions) {
            val servicesForAction = serviceActionToServices[action] ?: emptyList()
            val activitiesForAction = activityInfos.filter { it.actions.contains(action) }

            val hasService = servicesForAction.isNotEmpty()
            val hasActivity = activitiesForAction.isNotEmpty()

            if (activitiesForAction.size > 1) {
                for (activityInfo in activitiesForAction) {
                    report(activityInfo.element, "Duplicate watch face configuration activities found")
                }
            } else if (!hasService && hasActivity) {
                for (activityInfo in activitiesForAction) {
                    report(
                        activityInfo.element,
                        "Configuration activity found but no watch face service defines the corresponding `wearableConfigurationAction` metadata"
                    )
                }
            }

            if (hasService && !hasActivity) {
                for (service in servicesForAction) {
                    report(
                        service,
                        "Watch face service defines `wearableConfigurationAction` but no matching configuration activity was found"
                    )
                }
            }

            if (hasService && minSdkVersion < 30) {
                for (activityInfo in activitiesForAction) {
                    val hasRequiredCategory = activityInfo.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") ||
                            activityInfo.categories.contains("android.support.wearable.watchface.category.WEARABLE_CONFIGURATION")
                    if (!hasRequiredCategory) {
                        report(
                            activityInfo.element,
                            "Configuration activity is missing the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category (required for minSdkVersion < 30)"
                        )
                    }
                }
            }
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String? {
        if (hasAttributeNS(SdkConstants.ANDROID_URI, localName)) {
            val val1 = getAttributeNS(SdkConstants.ANDROID_URI, localName)
            if (val1.isNotEmpty()) return val1
        }
        if (hasAttribute("android:$localName")) {
            val val2 = getAttribute("android:$localName")
            if (val2.isNotEmpty()) return val2
        }
        if (hasAttribute(localName)) {
            val val3 = getAttribute(localName)
            if (val3.isNotEmpty()) return val3
        }
        return null
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, \
                which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if minSdkVersion is less than 30).
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
}