package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, " +
                "with the value WATCH_FACE_EDITOR, there should be an activity in the same package, which has " +
                "an intent filter for WATCH_FACE_EDITOR (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION " +
                "if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val ACTION_WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val META_DATA_SUFFIX = "wearableConfigurationAction"
    }

    override fun getApplicableElements(): Collection<String>? = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "manifest") return

        val pkg = element.getAttribute("package")
        if (pkg.isEmpty()) return

        val minSdk = context.mainProject.minSdkVersion?.apiLevel ?: 1
        val requiresCategory = minSdk < 30

        val servicesNeedingEditor = mutableListOf<Element>()
        val activitiesWithEditor = mutableListOf<Element>()

        val services = element.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (hasWearableConfigAction(service)) {
                servicesNeedingEditor.add(service)
            }
        }

        val activities = element.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            if (hasEditorIntentFilter(activity, requiresCategory)) {
                activitiesWithEditor.add(activity)
            }
        }

        if (servicesNeedingEditor.isNotEmpty()) {
            if (activitiesWithEditor.isEmpty()) {
                context.report(
                    ISSUE,
                    servicesNeedingEditor.first(),
                    context.getLocation(servicesNeedingEditor.first()),
                    "Missing watch face configuration activity for $ACTION_WATCH_FACE_EDITOR"
                )
            } else if (activitiesWithEditor.size > 1) {
                for (activity in activitiesWithEditor) {
                    context.report(
                        ISSUE,
                        activity,
                        context.getLocation(activity),
                        "Duplicate watch face configuration activities found"
                    )
                }
            }
        } else if (activitiesWithEditor.isNotEmpty()) {
            for (activity in activitiesWithEditor) {
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "Watch face configuration activity found without corresponding service metadata"
                )
            }
        }
    }

    private fun hasWearableConfigAction(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "meta-data") {
                val name = node.getAttributeNS(ANDROID_URI, "name")
                val value = node.getAttributeNS(ANDROID_URI, "value")
                if (name.endsWith(META_DATA_SUFFIX) && value == ACTION_WATCH_FACE_EDITOR) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasEditorIntentFilter(activity: Element, requiresCategory: Boolean): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "intent-filter") {
                var hasAction = false
                var hasCategory = !requiresCategory
                val filterChildren = node.childNodes
                for (j in 0 until filterChildren.length) {
                    val child = filterChildren.item(j)
                    if (child is Element) {
                        val name = child.getAttributeNS(ANDROID_URI, "name")
                        if (child.tagName == "action" && name == ACTION_WATCH_FACE_EDITOR) {
                            hasAction = true
                        }
                        if (child.tagName == "category" && name == CATEGORY_WEARABLE_CONFIGURATION) {
                            hasCategory = true
                        }
                    }
                }
                if (hasAction && hasCategory) return true
            }
        }
        return false
    }
}