package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val services = mutableListOf<Component>()
    private val activities = mutableListOf<Component>()

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun beforeCheckFile(context: Context) {
        services.clear()
        activities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                val metaData = element.getElementsByTagName(TAG_META_DATA)
                for (i in 0 until metaData.length) {
                    val meta = metaData.item(i) as? Element ?: continue
                    val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (name == META_DATA_NAME && value == WATCH_FACE_EDITOR) {
                        services.add(Component(element, context.manifestPackage))
                        break
                    }
                }
            }
            TAG_ACTIVITY -> {
                val filters = element.getElementsByTagName(TAG_INTENT_FILTER)
                for (i in 0 until filters.length) {
                    val filter = filters.item(i) as? Element ?: continue
                    if (filter.containsAction(WATCH_FACE_EDITOR)) {
                        activities.add(Component(element, context.manifestPackage))
                        break
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val minSdk = context.mainProject.minSdk
        val matchingActivities = activities.filter { it.matchesAction(minSdk) }

        for (service in services) {
            val matches = matchingActivities.filter { it.packageName == service.packageName }
            when {
                matches.isEmpty() -> {
                    context.report(
                        ISSUE,
                        context.getLocation(service.element),
                        "A watch face service declares wearableConfigurationAction=$WATCH_FACE_EDITOR but no activity handles that action."
                    )
                }
                matches.size > 1 -> {
                    context.report(
                        ISSUE,
                        context.getLocation(service.element),
                        "Duplicate watch face configuration activities found for action $WATCH_FACE_EDITOR."
                    )
                }
            }
        }

        for (activity in matchingActivities) {
            if (services.none { it.packageName == activity.packageName }) {
                context.report(
                    ISSUE,
                    context.getLocation(activity.element),
                    "Watch face configuration activity found without a corresponding watch face service metadata declaration."
                )
            }
        }

        services.clear()
        activities.clear()
    }

    private val XmlContext.manifestPackage: String
        get() = document.documentElement?.getAttribute(ATTR_PACKAGE) ?: ""

    private fun Element.containsAction(actionName: String): Boolean {
        val actions = getElementsByTagName(ELEMENT_ACTION)
        for (i in 0 until actions.length) {
            val action = actions.item(i) as? Element ?: continue
            if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == actionName) return true
        }
        return false
    }

    private fun Component.matchesAction(minSdk: Int): Boolean {
        val filters = element.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue
            if (!filter.containsAction(WATCH_FACE_EDITOR)) continue
            if (minSdk >= 30) return true
            val categories = filter.getElementsByTagName(ELEMENT_CATEGORY)
            for (j in 0 until categories.length) {
                val category = categories.item(j) as? Element ?: continue
                if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY) return true
            }
        }
        return false
    }

    private data class Component(val element: Element, val packageName: String)

    companion object {
        const val WATCH_FACE_EDITOR = "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        const val META_DATA_NAME = "wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                A watch face service that declares wearableConfigurationAction=
                com.google.android.wearable.watchface.WATCH_FACE_EDITOR must have exactly one
                corresponding activity in the same package with an intent filter for that action.
                If minSdkVersion is less than 30, the activity must also include the
                com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}