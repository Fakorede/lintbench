package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
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
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val document = element.ownerDocument ?: return
        analyzeManifest(context, document)
    }

    private fun analyzeManifest(context: XmlContext, document: Document) {
        val services = document.getElementsByTagName("service")
        var serviceHasEditorMeta = false
        var serviceMetaElement: Element? = null

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val meta = metaDatas.item(j) as Element
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (value.endsWith("WATCH_FACE_EDITOR")) {
                        serviceHasEditorMeta = true
                        serviceMetaElement = meta
                    }
                }
            }
        }

        val activities = document.getElementsByTagName("activity")
        val matchingActivities = mutableListOf<Element>()
        val activitiesMissingCategory = mutableListOf<Element>()

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                var hasEditorAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (actionName.endsWith("WATCH_FACE_EDITOR")) {
                        hasEditorAction = true
                        break
                    }
                }
                if (hasEditorAction) {
                    val categories = filter.getElementsByTagName("category")
                    var hasConfigCategory = false
                    for (k in 0 until categories.length) {
                        val category = categories.item(k) as Element
                        val catName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasConfigCategory = true
                            break
                        }
                    }

                    val minSdk = context.project.minSdkVersion.apiLevel
                    if (minSdk < 30 && !hasConfigCategory) {
                        activitiesMissingCategory.add(activity)
                    } else {
                        matchingActivities.add(activity)
                    }
                }
            }
        }

        val totalActivities = matchingActivities.size + activitiesMissingCategory.size

        if (totalActivities > 1) {
            val first = matchingActivities.firstOrNull() ?: activitiesMissingCategory.first()
            context.report(
                ISSUE,
                first,
                context.getLocation(first),
                "Duplicate watch face configuration activities found"
            )
            return
        }

        if (serviceHasEditorMeta) {
            if (matchingActivities.isEmpty() && activitiesMissingCategory.isEmpty()) {
                val location = serviceMetaElement?.let { context.getLocation(it) } ?: context.getLocation(document.documentElement)
                context.report(
                    ISSUE,
                    serviceMetaElement ?: document.documentElement,
                    location,
                    "A watch face service defines `wearableConfigurationAction` as `WATCH_FACE_EDITOR`, but no matching configuration activity was found."
                )
            } else if (matchingActivities.isEmpty() && activitiesMissingCategory.isNotEmpty()) {
                val missingActivity = activitiesMissingCategory.first()
                context.report(
                    ISSUE,
                    missingActivity,
                    context.getLocation(missingActivity),
                    "Watch face configuration activity is missing the required `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category for minSdkVersion < 30."
                )
            }
        } else {
            if (matchingActivities.isNotEmpty() || activitiesMissingCategory.isNotEmpty()) {
                val activity = matchingActivities.firstOrNull() ?: activitiesMissingCategory.first()
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "An activity is configured as a watch face editor, but no watch face service defines the corresponding `wearableConfigurationAction` metadata."
                )
            }
        }
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
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if minSdkVersion \
                is less than 30).
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