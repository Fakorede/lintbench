package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

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

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.MANIFEST_SCOPE

    private fun getAndroidAttribute(element: Element, localName: String): String {
        return element.getAttributeNS(SdkConstants.ANDROID_URI, localName).takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:$localName")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val services = root.getElementsByTagName("service")
        val servicesWithMeta = mutableListOf<Element>()

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val meta = metaDatas.item(j) as Element
                val name = getAndroidAttribute(meta, "name")
                val value = getAndroidAttribute(meta, "value")
                if ((name == "wearableConfigurationAction" || name.endsWith(".wearableConfigurationAction")) &&
                    (value == "WATCH_FACE_EDITOR" || value.endsWith(".WATCH_FACE_EDITOR"))
                ) {
                    servicesWithMeta.add(service)
                    break
                }
            }
        }

        val activities = root.getElementsByTagName("activity")
        val matchingActivities = mutableListOf<Element>()
        val activitiesMissingCategory = mutableListOf<Element>()

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            var hasAction = false
            var hasCategory = false

            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = getAndroidAttribute(action, "name")
                    if (actionName == "WATCH_FACE_EDITOR" || actionName.endsWith(".WATCH_FACE_EDITOR")) {
                        hasAction = true
                    }
                }
                val categories = filter.getElementsByTagName("category")
                for (k in 0 until categories.length) {
                    val category = categories.item(k) as Element
                    val catName = getAndroidAttribute(category, "name")
                    if (catName == "WEARABLE_CONFIGURATION" || catName.endsWith(".WEARABLE_CONFIGURATION")) {
                        hasCategory = true
                    }
                }
            }

            if (hasAction) {
                matchingActivities.add(activity)
                if (minSdkVersion < 30 && !hasCategory) {
                    activitiesMissingCategory.add(activity)
                }
            }
        }

        if (matchingActivities.size > 1) {
            for (activity in matchingActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
            return
        }

        if (servicesWithMeta.isNotEmpty() && matchingActivities.isEmpty()) {
            for (service in servicesWithMeta) {
                context.report(
                    ISSUE,
                    service,
                    context.getNameLocation(service),
                    "Watch face service defines WATCH_FACE_EDITOR configuration action, but no matching activity was found"
                )
            }
        }

        if (matchingActivities.isNotEmpty() && servicesWithMeta.isEmpty()) {
            for (activity in matchingActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Activity defines WATCH_FACE_EDITOR intent filter, but no watch face service defines the corresponding wearableConfigurationAction metadata"
                )
            }
        }

        if (minSdkVersion < 30) {
            for (activity in activitiesMissingCategory) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Activity with WATCH_FACE_EDITOR action must also include WEARABLE_CONFIGURATION category when minSdkVersion < 30"
                )
            }
        }
    }
}