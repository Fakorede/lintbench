package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private const val METADATA_WEARABLE_CONFIGURATION_ACTION =
    "com.google.android.wearable.watchface.wearableConfigurationAction"
private const val VALUE_WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
private const val CATEGORY_WEARABLE_CONFIGURATION =
    "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val metaData = element.findMetadata(METADATA_WEARABLE_CONFIGURATION_ACTION) ?: return
        val actionValue = metaData.getAndroidAttr(SdkConstants.ATTR_VALUE) ?: return
        if (actionValue != VALUE_WATCH_FACE_EDITOR) return

        val servicePackage = getComponentPackage(context, element)
        val minSdk = context.mainProject?.minSdkVersion?.apiLevel ?: Int.MAX_VALUE
        val requiresConfigCategory = minSdk < 30

        val activities = context.document
            .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
            .asElementList()
        val activityAliases = context.document
            .getElementsByTagName(SdkConstants.TAG_ACTIVITY_ALIAS)
            .asElementList()

        val found = (activities + activityAliases).any { activity ->
            getComponentPackage(context, activity) == servicePackage &&
                activity.hasEditorIntentFilter(requiresConfigCategory)
        }

        if (!found) {
            val message = buildString {
                append("No activity in the same package handles the ")
                append(VALUE_WATCH_FACE_EDITOR)
                append(" configuration action")
                if (requiresConfigCategory) {
                    append(" with the ")
                    append(CATEGORY_WEARABLE_CONFIGURATION)
                    append(" category")
                }
                append(".")
            }
            context.report(
                ISSUE,
                metaData,
                context.getElementLocation(metaData),
                message
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Watch face configuration action must have a matching activity",
            explanation = """
                When a watch face service declares a `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be a corresponding activity in \
                the same package that handles that action. If the `minSdkVersion` is less than \
                30, the activity's intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}

private fun Element.findMetadata(name: String): Element? {
    return getElementsByTagName(SdkConstants.TAG_METADATA)
        .asElementList()
        .firstOrNull {
            it.getAndroidAttr(SdkConstants.ATTR_NAME) == name
        }
}

private fun Element.hasEditorIntentFilter(requiresCategory: Boolean): Boolean {
    val filters = getElementsByTagName(SdkConstants.TAG_INTENT_FILTER).asElementList()
    return filters.any { filter ->
        val actions = filter.childAttributeValues(SdkConstants.TAG_ACTION)
        val categories = filter.childAttributeValues(SdkConstants.TAG_CATEGORY)
        VALUE_WATCH_FACE_EDITOR in actions &&
            (!requiresCategory || CATEGORY_WEARABLE_CONFIGURATION in categories)
    }
}

private fun Element.childAttributeValues(tag: String): List<String> {
    return getElementsByTagName(tag)
        .asElementList()
        .mapNotNull { it.getAndroidAttr(SdkConstants.ATTR_NAME) }
        .filter { it.isNotBlank() }
}

private fun getComponentPackage(context: XmlContext, element: Element): String {
    val manifestPackage = context.document.documentElement?.getAttribute(
        SdkConstants.ATTR_PACKAGE
    ) ?: ""
    val name = element.getAndroidAttr(SdkConstants.ATTR_NAME) ?: ""
    return when {
        name.startsWith('.') -> manifestPackage + name
        '.' in name -> name.substringBeforeLast('.')
        else -> manifestPackage
    }
}

private fun Element.getAndroidAttr(localName: String): String? {
    val value = getAttributeNS(SdkConstants.ANDROID_URI, localName)
    return if (value.isNotBlank()) value else null
}

private fun NodeList.asElementList(): List<Element> {
    return (0 until length).mapNotNull { item(it) as? Element }
}