package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_BOOL
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslucentViewDetector : ResourceXmlDetector() {

    private val styles = mutableMapOf<String, StyleInfo>()
    private val bools = mutableMapOf<String, Boolean>()

    override fun getApplicableElements(): Collection<String> = listOf(TAG_ACTIVITY)

    override fun beforeCheckProject(context: Context) {
        styles.clear()
        bools.clear()

        for (dir in context.project.resourceDirectories) {
            if (!dir.isDirectory) continue
            for (file in dir.walkTopDown()) {
                if (!file.isFile || file.extension != "xml") continue
                parseResourceFile(file)
            }
        }
    }

    private fun parseResourceFile(file: File) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        try {
            val doc = factory.newDocumentBuilder().parse(file)

            val styleNodes = doc.getElementsByTagName(TAG_STYLE)
            for (i in 0 until styleNodes.length) {
                val style = styleNodes.item(i) as? Element ?: continue
                val name = style.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: continue
                val parent = style.getAttribute(ATTR_PARENT).takeIf { it.isNotEmpty() }

                val items = mutableMapOf<String, String>()
                val itemNodes = style.getElementsByTagName(TAG_ITEM)
                for (j in 0 until itemNodes.length) {
                    val item = itemNodes.item(j) as? Element ?: continue
                    val rawName = item.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: continue
                    val normalizedName = if (rawName.startsWith("android:")) rawName.substring(8) else rawName
                    items[normalizedName] = item.textContent?.trim() ?: ""
                }

                styles[name] = StyleInfo(name, parent, items)
            }

            val boolNodes = doc.getElementsByTagName(TAG_BOOL)
            for (i in 0 until boolNodes.length) {
                val bool = boolNodes.item(i) as? Element ?: continue
                val name = bool.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: continue
                bools[name] = bool.textContent?.trim().equals(VALUE_TRUE, ignoreCase = true)
            }
        } catch (_: Throwable) {
            // Ignore malformed or unreadable resource files.
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.project.targetSdk < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return
        val orientation = orientationAttr.value ?: return
        if (!isFixedOrientation(orientation)) {
            return
        }

        val themeValue = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
            .ifEmpty { getApplicationTheme(element) }
        if (themeValue.isEmpty()) {
            return
        }

        if (isTranslucentTheme(themeValue)) {
            context.report(
                ISSUE,
                orientationAttr,
                context.getLocation(orientationAttr),
                "Mixing screenOrientation and translucency is not supported when targetSdkVersion is O or greater"
            )
        }
    }

    private fun getApplicationTheme(activity: Element): String {
        val document = activity.ownerDocument ?: return ""
        val apps = document.getElementsByTagName(TAG_APPLICATION)
        if (apps.length == 0) {
            return ""
        }
        val app = apps.item(0) as? Element ?: return ""
        return app.getAttributeNS(ANDROID_URI, ATTR_THEME)
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        for (part in orientation.split('|')) {
            when (part.trim().lowercase()) {
                "portrait",
                "landscape",
                "reverseportrait",
                "reverselandscape",
                "sensorportrait",
                "sensorlandscape",
                "userportrait",
                "userlandscape",
                "locked" -> return true
                else -> continue
            }
        }
        return false
    }

    private fun isTranslucentTheme(themeValue: String): Boolean {
        val ref = resolveStyleReference(themeValue) ?: return false
        if (ref.framework) {
            return ref.name.contains("Translucent", ignoreCase = true)
        }
        val info = styles[ref.name]
        return if (info != null) {
            isStyleTranslucent(info, mutableSetOf())
        } else {
            ref.name.contains("Translucent", ignoreCase = true)
        }
    }

    private fun isStyleTranslucent(info: StyleInfo, visiting: MutableSet<String>): Boolean {
        if (!visiting.add(info.name)) {
            return false
        }

        val raw = info.items["windowIsTranslucent"]
        if (raw != null) {
            val isTrue: Boolean? = when {
                raw.startsWith("@bool/") -> bools[raw.substringAfterLast('/')] ?: false
                raw.startsWith("@android:bool/") -> false
                raw.startsWith("?") -> null
                raw.equals("true", ignoreCase = true) -> true
                raw.equals("false", ignoreCase = true) -> false
                else -> false
            }

            when (isTrue) {
                true -> return true
                false -> return false
                null -> { /* fall through to parent */ }
            }
        }

        val parentRef = info.parent?.let { resolveStyleReference(it) }
            ?: if (info.name.contains('.')) {
                resolveStyleReference(info.name.substringBeforeLast('.'))
            } else null
            ?: return false

        return isTranslucentRef(parentRef, visiting)
    }

    private fun isTranslucentRef(ref: StyleRef, visiting: MutableSet<String>): Boolean {
        if (ref.framework) {
            return ref.name.contains("Translucent", ignoreCase = true)
        }
        val next = styles[ref.name]
            ?: return ref.name.contains("Translucent", ignoreCase = true)
        return isStyleTranslucent(next, visiting)
    }

    private fun resolveStyleReference(value: String): StyleRef? {
        return when {
            value.isEmpty() -> null
            value.startsWith("@android:style/") -> StyleRef(value.substringAfterLast('/'), true)
            value.startsWith("@*android:style/") -> StyleRef(value.substringAfterLast('/'), true)
            value.startsWith("@style/") -> StyleRef(value.substringAfterLast('/'), false)
            value.startsWith("@") -> null
            else -> StyleRef(value, false)
        }
    }

    private data class StyleInfo(
        val name: String,
        val parent: String?,
        val items: Map<String, String>
    )

    private data class StyleRef(
        val name: String,
        val framework: Boolean
    )

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme is not supported
                on apps with `targetSdkVersion` O (API 26) or greater. When an activity behind
                your translucent activity requests a different orientation, the system can only
                honor one request and prefers the non-translucent activity. On Android O and
                higher, this state causes an exception.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}