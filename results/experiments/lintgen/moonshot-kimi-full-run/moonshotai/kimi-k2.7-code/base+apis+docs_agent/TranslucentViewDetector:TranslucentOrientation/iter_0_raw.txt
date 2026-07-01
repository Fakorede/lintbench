package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

private const val TAG_ACTIVITY = "activity"
private const val TAG_APPLICATION = "application"
private const val TAG_STYLE = "style"
private const val TAG_ITEM = "item"

private const val ATTR_THEME = "theme"
private const val ATTR_NAME = "name"
private const val ATTR_PARENT = "parent"
private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"

private const val ITEM_WINDOW_IS_TRANSLUCENT = "android:windowIsTranslucent"
private const val ITEM_WINDOW_IS_TRANSLUCENT_NO_NS = "windowIsTranslucent"

class TranslucentViewDetector : Detector(), XmlScanner {

    private data class ActivityInfo(
        val element: Element,
        val theme: String?,
        val orientation: String?
    )

    private val activitiesByFile = mutableMapOf<String, MutableList<ActivityInfo>>()
    private val applicationThemeByFile = mutableMapOf<String, String?>()
    private val styleParents = mutableMapOf<String, String?>()
    private val translucentStyles = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_ACTIVITY, TAG_APPLICATION, TAG_STYLE, TAG_ITEM)

    override fun beforeCheckEachProject(context: Context) {
        activitiesByFile.clear()
        applicationThemeByFile.clear()
        styleParents.clear()
        translucentStyles.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_ACTIVITY -> {
                val theme = element.resolveTheme()
                val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                    .takeIf { it.isNotBlank() }
                activitiesByFile.getOrPut(context.file.path) { mutableListOf() }
                    .add(ActivityInfo(element, theme, orientation))
            }

            TAG_APPLICATION -> {
                applicationThemeByFile[context.file.path] = element.resolveTheme()
            }

            TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotBlank() } ?: return
                val parentAttr = element.getAttribute(ATTR_PARENT).takeIf { it.isNotBlank() }
                val dotParent = if (parentAttr == null && name.contains('.')) {
                    name.substringBeforeLast('.')
                } else null

                styleParents[name] = parentAttr?.stripStyleRef() ?: dotParent

                if (name.contains("Translucent", ignoreCase = true)) {
                    translucentStyles.add(name)
                }
            }

            TAG_ITEM -> {
                val parentStyle = findParentStyleName(element) ?: return
                val itemName = element.getAttribute(ATTR_NAME).takeIf { it.isNotBlank() } ?: return

                if (itemName == ITEM_WINDOW_IS_TRANSLUCENT || itemName == ITEM_WINDOW_IS_TRANSLUCENT_NO_NS) {
                    val value = element.textContent?.trim()
                    if (value == "true" || value == "@android:bool/true") {
                        translucentStyles.add(parentStyle)
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val root = context.document.documentElement ?: return
        if (root.tagName != "manifest") return

        if ((context.mainProject.targetSdkVersion?.featureLevel ?: 0) < 26) return

        val filePath = context.file.path
        val appTheme = applicationThemeByFile[filePath]
        val activities = activitiesByFile[filePath] ?: return

        for (activity in activities) {
            val orientation = activity.orientation ?: continue
            if (!isFixedOrientation(orientation)) continue

            val theme = activity.theme ?: appTheme
            if (theme != null && isTranslucentTheme(theme)) {
                val attr = activity.element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                val location = if (attr != null) context.getLocation(attr) else context.getLocation(activity.element)

                context.report(
                    ISSUE,
                    location,
                    "Using a fixed screenOrientation with a translucent theme is not supported when targetSdkVersion is O or greater"
                )
            }
        }
    }

    private fun Element.resolveTheme(): String? {
        val raw = getAttributeNS(ANDROID_URI, ATTR_THEME).takeIf { it.isNotBlank() } ?: return null
        if (raw.startsWith("?")) return null
        return raw.stripStyleRef()
    }

    private fun String.stripStyleRef(): String {
        return when {
            startsWith("@style/") -> substring(7)
            startsWith("@android:style/") -> substring(15)
            startsWith("@*android:style/") -> substring(16)
            startsWith("@") && contains('/') -> substring(indexOf('/') + 1)
            else -> this
        }
    }

    private fun findParentStyleName(element: Element): String? {
        var node: Node? = element.parentNode
        while (node != null) {
            if (node is Element && node.tagName == TAG_STYLE) {
                return node.getAttribute(ATTR_NAME).takeIf { it.isNotBlank() }
            }
            node = node.parentNode
        }
        return null
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        if (orientation.startsWith("@")) return false
        return when (orientation) {
            "unspecified", "behind", "sensor", "fullSensor", "user", "fullUser" -> false
            else -> true
        }
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        if (theme.contains("Translucent", ignoreCase = true)) return true
        return isTranslucentStyle(theme)
    }

    private fun isTranslucentStyle(name: String): Boolean {
        var current: String? = name
        val visited = mutableSetOf<String>()
        while (current != null && current !in visited) {
            visited.add(current)
            if (current in translucentStyles) return true
            current = styleParents[current]
        }
        return false
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with
                `targetSdkVersion` O or greater, since there can be another activity visible behind your activity
                with a conflicting orientation request. Devices running platform version O or greater will throw
                an exception if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}