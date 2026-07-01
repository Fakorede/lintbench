package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
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

        private const val META_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION_EDITOR = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val ACTION_EDITOR_SHORT = "WATCH_FACE_EDITOR"
        private const val CATEGORY_CONFIG = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val services = root.getElementsByTagName(SdkConstants.TAG_SERVICE)
        val activities = root.getElementsByTagName(SdkConstants.TAG_ACTIVITY)

        class ServiceMeta(val element: Element, val metaDataElement: Element)
        class ActivityFilter(val element: Element, val filterElement: Element, val hasCategory: Boolean)

        val servicesWithMeta = mutableListOf<ServiceMeta>()
        val activitiesWithFilter = mutableListOf<ActivityFilter>()

        // Find matching services
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaNodes = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
            for (j in 0 until metaNodes.length) {
                val meta = metaNodes.item(j) as Element
                val name = meta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = meta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == META_NAME && (value == ACTION_EDITOR || value == ACTION_EDITOR_SHORT)) {
                    servicesWithMeta.add(ServiceMeta(service, meta))
                }
            }
        }

        // Find matching activities
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val filterNodes = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
            for (j in 0 until filterNodes.length) {
                val filter = filterNodes.item(j) as Element
                val actionNodes = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
                var hasAction = false
                for (k in 0 until actionNodes.length) {
                    val action = actionNodes.item(k) as Element
                    val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (actionName == ACTION_EDITOR || actionName == ACTION_EDITOR_SHORT) {
                        hasAction = true
                        break
                    }
                }

                if (hasAction) {
                    val categoryNodes = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
                    var hasCategory = false
                    for (k in 0 until categoryNodes.length) {
                        val category = categoryNodes.item(k) as Element
                        val catName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (catName == CATEGORY_CONFIG) {
                            hasCategory = true
                            break
                        }
                    }
                    activitiesWithFilter.add(ActivityFilter(activity, filter, hasCategory))
                }
            }
        }

        val serviceCount = servicesWithMeta.size
        val activityCount = activitiesWithFilter.size

        // 1. If there is a service, there must be a matching activity.
        if (serviceCount > 0 && activityCount == 0) {
            for (serviceMeta in servicesWithMeta) {
                context.report(
                    ISSUE,
                    serviceMeta.metaDataElement,
                    context.getLocation(serviceMeta.metaDataElement),
                    "Watch face service defines wearableConfigurationAction but no matching configuration activity was found"
                )
            }
        }

        // 2. If there is an activity, there must be a matching service.
        if (activityCount > 0 && serviceCount == 0) {
            for (actFilter in activitiesWithFilter) {
                context.report(
                    ISSUE,
                    actFilter.filterElement,
                    context.getLocation(actFilter.filterElement),
                    "Configuration activity found but no watch face service defines the corresponding wearableConfigurationAction metadata"
                )
            }
        }

        // 3. Duplicate watch face configuration activities found
        if (activityCount > 1) {
            for (actFilter in activitiesWithFilter) {
                context.report(
                    ISSUE,
                    actFilter.element,
                    context.getLocation(actFilter.element),
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        // 4. Category check for minSdkVersion < 30
        val minSdkVersion = context.project.minSdkVersion.apiLevel
        if (minSdkVersion < 30 && activityCount > 0) {
            for (actFilter in activitiesWithFilter) {
                if (!actFilter.hasCategory) {
                    context.report(
                        ISSUE,
                        actFilter.filterElement,
                        context.getLocation(actFilter.filterElement),
                        "For minSdkVersion < 30, the watch face configuration activity must include the WEARABLE_CONFIGURATION category in its intent-filter"
                    )
                }
            }
        }
    }
}