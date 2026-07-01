package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val WEARABLE_CONFIGURATION_ACTION =
    "com.google.android.wearable.watchface.WEARABLE_CONFIGURATION_ACTION"
private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
private const val WEARABLE_CONFIGURATION_CATEGORY =
    "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.TAG_MANIFEST) {
            return
        }

        val requiredMetadata = mutableListOf<Element>()
        val activityFilters = mutableListOf<Pair<Set<String>, Set<String>>>()

        val application = element.getElementsByTagName(SdkConstants.TAG_APPLICATION)
            .item(0) as? Element ?: return

        val children = application.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            val child = node as Element
            when (child.tagName) {
                SdkConstants.TAG_SERVICE -> {
                    findMetadata(child)?.let { requiredMetadata.add(it) }
                }
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_ACTIVITY_ALIAS -> {
                    activityFilters.add(parseActivityFilters(child))
                }
            }
        }

        val minSdk = context.mainProject.minSdkVersion.featureLevel
        val hasMatchingActivity = activityFilters.any { (actions, categories) ->
            WATCH_FACE_EDITOR in actions &&
                (minSdk >= 30 || WEARABLE_CONFIGURATION_CATEGORY in categories)
        }

        if (requiredMetadata.isNotEmpty() && !hasMatchingActivity) {
            val message = buildErrorMessage(minSdk)
            requiredMetadata.forEach { metadata ->
                context.report(
                    ISSUE,
                    metadata,
                    context.getLocation(metadata),
                    message
                )
            }
        }
    }

    private fun findMetadata(service: Element): Element? {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            val child = node as Element
            if (child.tagName != SdkConstants.TAG_META_DATA) continue

            val name = getAndroidAttr(child, SdkConstants.ATTR_NAME) ?: continue
            val value = getAndroidAttr(child, SdkConstants.ATTR_VALUE) ?: continue

            if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                return child
            }
        }
        return null
    }

    private fun parseActivityFilters(activity: Element): Pair<Set<String>, Set<String>> {
        val actions = mutableSetOf<String>()
        val categories = mutableSetOf<String>()

        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            val child = node as Element
            if (child.tagName != SdkConstants.TAG_INTENT_FILTER) continue

            val filterChildren = child.childNodes
            for (j in 0 until filterChildren.length) {
                val filterNode = filterChildren.item(j)
                if (filterNode.nodeType != Node.ELEMENT_NODE) continue

                val filterChild = filterNode as Element
                when (filterChild.tagName) {
                    SdkConstants.TAG_ACTION -> {
                        getAndroidAttr(filterChild, SdkConstants.ATTR_NAME)?.let(actions::add)
                    }
                    SdkConstants.TAG_CATEGORY -> {
                        getAndroidAttr(filterChild, SdkConstants.ATTR_NAME)?.let(categories::add)
                    }
                }
            }
        }

        return actions to categories
    }

    private fun getAndroidAttr(element: Element, attr: String): String? {
        val value = element.getAttributeNS(SdkConstants.ANDROID_URI, attr)
        return if (value.isNotBlank()) value else null
    }

    private fun buildErrorMessage(minSdk: Int): String {
        val base = "Watch face service declares a wearable configuration action with value " +
            "\"$WATCH_FACE_EDITOR\" but no activity in the same package has an intent filter " +
            "with action \"$WATCH_FACE_EDITOR\""
        return if (minSdk < 30) {
            "$base and category \"$WEARABLE_CONFIGURATION_CATEGORY\""
        } else {
            base
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares a metadata element with
                android:name="$WEARABLE_CONFIGURATION_ACTION" and
                android:value="$WATCH_FACE_EDITOR", there must be a corresponding activity in the
                same package with an intent filter whose action is "$WATCH_FACE_EDITOR".

                If the app's minSdkVersion is less than 30, that intent filter must also include
                the category "$WEARABLE_CONFIGURATION_CATEGORY".

                See https://developer.android.com/training/wearables/watch-faces/configuration
                for more details.
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