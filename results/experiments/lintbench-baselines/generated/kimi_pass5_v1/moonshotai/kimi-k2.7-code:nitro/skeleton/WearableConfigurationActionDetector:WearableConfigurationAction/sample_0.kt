package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val EDITOR_ACTION = "WATCH_FACE_EDITOR"
        private const val CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                A watch face service that declares the wearable configuration action \
                <code>WATCH_FACE_EDITOR</code> must have a matching activity in the same \
                package with an intent filter for that action. When <code>minSdkVersion</code> \
                is below 30, the intent filter must also include the \
                <code>WEARABLE_CONFIGURATION</code> category.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val manifestFile = context.mainProject.mergeManifest ?: context.file ?: return
        if (!manifestFile.isFile) return

        val document = context.client.xmlParser.parseXml(manifestFile) ?: return
        val root = document.documentElement ?: return
        val packageName = root.getAttribute("package") ?: ""

        val application = root.children("application").firstOrNull() ?: return

        val requiredServices = application.children("service").mapNotNull { service ->
            service.children("meta-data").find { meta ->
                meta.attr("name") == CONFIGURATION_ACTION_METADATA &&
                    meta.attr("value") == EDITOR_ACTION
            }
        }

        if (requiredServices.isEmpty()) return

        val minSdk = context.mainProject.minSdkVersion
        val hasMatchingActivity = application.children("activity").any { activity ->
            val activityName = activity.attr("name") ?: return@any false
            if (!isInSamePackage(packageName, activityName)) return@any false
            activity.children("intent-filter").any { filter ->
                filter.hasAction(EDITOR_ACTION) &&
                    (minSdk >= 30 || filter.hasCategory(CONFIGURATION_CATEGORY))
            }
        }

        if (hasMatchingActivity) return

        for (meta in requiredServices) {
            val location = context.client.xmlParser.getLocation(context, meta)
                ?: Location.create(manifestFile)
            context.report(
                ISSUE,
                location,
                "Watch face service declares `${CONFIGURATION_ACTION_METADATA}` with value " +
                    "`${EDITOR_ACTION}` but no matching activity with that action " +
                    (if (minSdk < 30) "and category `${CONFIGURATION_CATEGORY}` " else "") +
                    "was found in the same package."
            )
        }
    }

    private fun isInSamePackage(packageName: String, componentName: String): Boolean {
        if (packageName.isEmpty()) return true
        val fullName = when {
            componentName.startsWith(".") -> packageName + componentName
            "." in componentName -> componentName
            else -> "$packageName.$componentName"
        }
        return fullName.startsWith("$packageName.")
    }

    private fun org.w3c.dom.Element.children(localName: String): List<org.w3c.dom.Element> {
        val result = mutableListOf<org.w3c.dom.Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val childName = child.localName
                ?: child.nodeName?.substringAfter(":", child.nodeName)
                ?: continue
            if (childName == localName) result.add(child)
        }
        return result
    }

    private fun org.w3c.dom.Element.attr(localName: String): String? {
        val attrs = attributes ?: return null
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? org.w3c.dom.Attr ?: continue
            val name = attr.localName
                ?: attr.name?.substringAfter(":", attr.name)
                ?: continue
            if (name == localName) return attr.value
        }
        return null
    }

    private fun org.w3c.dom.Element.hasAction(name: String): Boolean =
        children("action").any { it.attr("name") == name }

    private fun org.w3c.dom.Element.hasCategory(name: String): Boolean =
        children("category").any { it.attr("name") == name }
}