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

        val services = root.getElementsByTagName("service")
        val activities = root.getElementsByTagName("activity")

        val serviceActionToServices = mutableMapOf<String, MutableList<Element>>()

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as Element
                val name = metaData.getAndroidAttribute("name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val value = metaData.getAndroidAttribute("value")
                    if (!value.isNullOrEmpty()) {
                        serviceActionToServices.getOrPut(value) { mutableListOf() }.add(service)
                    }
                }
            }
        }

        class ActivityInfo(val element: Element, val actions: Set<String>, val categories: Set<String>)
        val activityInfos = mutableListOf<ActivityInfo>()

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            val actions = mutableSetOf<String>()
            val categories = mutableSetOf<String>()
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                
                val actionElements = filter.getElementsByTagName("action")
                for (k in 0 until actionElements.length) {
                    val actionEl = actionElements.item(k) as Element
                    val actionName = actionEl.getAndroidAttribute("name")
                    if (!actionName.isNullOrEmpty()) {
                        actions.add(actionName)
                    }
                }
                
                val categoryElements = filter.getElementsByTagName("category")
                for (k in 0 until categoryElements.length) {
                    val catEl = categoryElements.item(k) as Element
                    val catName = catEl.getAndroidAttribute("name")
                    if (!catName.isNullOrEmpty()) {
                        categories.add(catName)
                    }
                }
            }
            activityInfos.add(ActivityInfo(activity, actions, categories))
        }

        val targetActions = mutableSetOf<String>()
        targetActions.addAll(serviceActionToServices.keys)

        for (activityInfo in activityInfos) {
            if (activityInfo.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")) {
                targetActions.addAll(activityInfo.actions)
            }
            for (action in activityInfo.actions) {
                if (action.contains("WATCH_FACE_EDITOR")) {
                    targetActions.add(action)
                }
            }
        }

        val minSdkVersion = context.project.minSdkVersion.apiLevel
        val reported = mutableSetOf<String>()

        fun report(element: Element, message: String) {
            val key = "${element.hashCode()}:$message"
            if (reported.add(key)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
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
                    if (!activityInfo.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")) {
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
        val attr = getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (attr.isNotEmpty()) return attr
        val attr2 = getAttribute("android:$localName")
        if (attr2.isNotEmpty()) return attr2
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