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

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        val manifest = document.documentElement ?: return
        if (manifest.tagName != "manifest") return

        val services = manifest.getElementsByTagName("service")
        val activities = manifest.getElementsByTagName("activity")

        val servicesWithMeta = mutableListOf<Element>()
        val metaDataElements = mutableListOf<Element>()

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val meta = metaDatas.item(j) as Element
                val name = meta.getAndroidAttribute("name")
                val value = meta.getAndroidAttribute("value")
                if ((name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction") &&
                    (value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR")
                ) {
                    servicesWithMeta.add(service)
                    metaDataElements.add(meta)
                }
            }
        }

        val activitiesWithAction = mutableSetOf<Element>()
        val intentFiltersWithAction = mutableListOf<Element>()
        val missingCategoryFilters = mutableListOf<Element>()

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                var hasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAndroidAttribute("name")
                    if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || actionName == "WATCH_FACE_EDITOR") {
                        hasAction = true
                        break
                    }
                }
                if (hasAction) {
                    activitiesWithAction.add(activity)
                    intentFiltersWithAction.add(filter)

                    if (minSdkVersion < 30) {
                        val categories = filter.getElementsByTagName("category")
                        var hasCategory = false
                        for (k in 0 until categories.length) {
                            val cat = categories.item(k) as Element
                            val catName = cat.getAndroidAttribute("name")
                            if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || catName == "WEARABLE_CONFIGURATION") {
                                hasCategory = true
                                break
                            }
                        }
                        if (!hasCategory) {
                            missingCategoryFilters.add(filter)
                        }
                    }
                }
            }
        }

        // 1. Check for duplicates
        if (activitiesWithAction.size > 1) {
            for (activity in activitiesWithAction) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        // 2. Check "if and only if"
        val hasServiceMeta = servicesWithMeta.isNotEmpty()
        val hasActivityAction = activitiesWithAction.isNotEmpty()

        if (hasServiceMeta && !hasActivityAction) {
            for (meta in metaDataElements) {
                context.report(
                    ISSUE,
                    meta,
                    context.getLocation(meta),
                    "A watch face service defines `wearableConfigurationAction` but no matching configuration activity was found"
                )
            }
        } else if (!hasServiceMeta && hasActivityAction) {
            for (filter in intentFiltersWithAction) {
                context.report(
                    ISSUE,
                    filter,
                    context.getLocation(filter),
                    "An activity is configured as a watch face editor, but no watch face service defines the corresponding `wearableConfigurationAction` metadata"
                )
            }
        }

        // 3. Check category for minSdkVersion < 30
        if (minSdkVersion < 30) {
            for (filter in missingCategoryFilters) {
                context.report(
                    ISSUE,
                    filter,
                    context.getLocation(filter),
                    "Watch face configuration activity intent-filter must include `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` when minSdkVersion < 30"
                )
            }
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String {
        return this.getAttributeNS(SdkConstants.ANDROID_URI, localName).takeIf { it.isNotEmpty() }
            ?: this.getAttribute("android:$localName")
    }
}