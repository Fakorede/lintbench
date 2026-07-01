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
import com.android.utils.XmlUtils
import org.w3c.dom.Element
import java.io.File

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.
            """,
            moreInfo = "https://developer.android.com/reference/android/R.attr.html#windowIsTranslucent",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val WINDOW_IS_TRANSLUCENT = "android:windowIsTranslucent"
        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.file.name.equals(SdkConstants.ANDROID_MANIFEST_XML, ignoreCase = true)) {
            return
        }

        if (context.project.targetSdkVersion < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            SdkConstants.ATTR_SCREEN_ORIENTATION
        ) ?: return
        val orientation = orientationAttr.value ?: return
        if (!isFixedOrientation(orientation)) {
            return
        }

        val theme = getActivityTheme(context, element) ?: return
        if (isTranslucentTheme(context, theme)) {
            context.report(
                ISSUE,
                orientationAttr,
                context.getLocation(orientationAttr),
                "Should not restrict orientation with a translucent theme"
            )
        }
    }

    private fun getActivityTheme(context: XmlContext, activity: Element): String? {
        activity.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)?.value?.let { return it }

        val manifest = activity.ownerDocument.documentElement
        val application = manifest.getElementsByTagName(SdkConstants.TAG_APPLICATION).item(0) as? Element
        return application?.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)?.value
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return FIXED_ORIENTATIONS.contains(orientation)
    }

    private fun isTranslucentTheme(context: XmlContext, theme: String): Boolean {
        val name = when {
            theme.startsWith("@style/") -> theme.substring("@style/".length)
            theme.startsWith("@android:style/") -> return theme.contains("Translucent")
            theme.startsWith("@*android:style/") -> return theme.contains("Translucent")
            else -> return false
        }

        return isTranslucentStyle(context, name, mutableSetOf())
    }

    private fun isTranslucentStyle(context: XmlContext, name: String, visited: MutableSet<String>): Boolean {
        if (!visited.add(name)) {
            return false
        }

        for (resDir in context.project.resourceDirectories) {
            val valuesDir = File(resDir, SdkConstants.FD_RES_VALUES)
            if (!valuesDir.isDirectory) {
                continue
            }

            val files = valuesDir.listFiles { file -> file.isFile && file.name.endsWith(SdkConstants.DOT_XML) }
            if (files == null) {
                continue
            }

            for (file in files) {
                val content = context.client.readFile(file)
                val document = XmlUtils.parseDocumentSilently(content, true) ?: continue

                val styles = document.getElementsByTagName(SdkConstants.TAG_STYLE)
                for (i in 0 until styles.length) {
                    val style = styles.item(i) as? Element ?: continue
                    if (style.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) != name) {
                        continue
                    }

                    val items = style.getElementsByTagName(SdkConstants.TAG_ITEM)
                    for (j in 0 until items.length) {
                        val item = items.item(j) as? Element ?: continue
                        val itemName = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (itemName == WINDOW_IS_TRANSLUCENT && item.textContent.trim() == "true") {
                            return true
                        }
                    }

                    val parent = style.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PARENT)
                    if (parent.isNotEmpty()) {
                        val parentName = when {
                            parent.startsWith("@style/") -> parent.substring("@style/".length)
                            parent.startsWith("@android:style/") -> return parent.contains("Translucent")
                            parent.startsWith("@*android:style/") -> return parent.contains("Translucent")
                            parent.contains('/') -> parent.substringAfterLast('/')
                            else -> parent
                        }
                        if (isTranslucentStyle(context, parentName, visited)) {
                            return true
                        }
                    }

                    return false
                }
            }
        }

        val dotIndex = name.lastIndexOf('.')
        if (dotIndex > 0) {
            val parentName = name.substring(0, dotIndex)
            if (isTranslucentStyle(context, parentName, visited)) {
                return true
            }
        }

        return false
    }
}