package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.w3c.dom.Attr
import org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ISSUE_ID = "TranslucentOrientation"
        private const val MIN_TARGET_SDK = 26

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = ISSUE_ID,
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme is not supported
                on apps with `targetSdkVersion` O or greater. There can be another activity visible
                behind your activity with a conflicting orientation request, and the system can only
                honor one of them. Devices running platform version O or greater will throw an
                `IllegalStateException` if this state is detected. Consider using a non-translucent
                theme or removing the fixed screen orientation.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val translucentThemes = mutableSetOf<String>()
    private val translucentActivities = mutableSetOf<String>()
    private var applicationTheme: String? = null

    override fun beforeCheckEachProject(context: Context) {
        translucentThemes.clear()
        translucentActivities.clear()
        applicationTheme = null
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? =
        listOf("activity", "style", "item")

    override fun getApplicableAttributes(): Collection<String>? =
        listOf("theme")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        if (attribute.localName == "theme" && owner.localName == "application") {
            applicationTheme = normalizeTheme(attribute.value)
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.localName) {
            "style" -> visitStyleElement(element)
            "item" -> visitItemElement(element)
            "activity" -> visitActivityElement(context, element)
        }
    }

    private fun visitStyleElement(element: Element) {
        val name = getAttributeValue(element, "name").ifBlank { return }
        val normalized = normalizeTheme(name)
        if (normalized.contains("Translucent")) {
            translucentThemes.add(normalized)
        }
        val parent = getAttributeValue(element, "parent")
        if (parent.isNotBlank() && normalizeTheme(parent).contains("Translucent")) {
            translucentThemes.add(normalized)
        }
    }

    private fun visitItemElement(element: Element) {
        val name = getAttributeValue(element, "name")
        if (name != "android:windowIsTranslucent") return
        val value = element.textContent?.trim() ?: return
        if (value != "true") return
        val parentStyle = findParentStyle(element) ?: return
        translucentThemes.add(normalizeTheme(parentStyle))
    }

    private fun visitActivityElement(context: XmlContext, element: Element) {
        val className = getActivityClassName(element) ?: return
        val orientation = getAttributeValue(element, "screenOrientation")
        if (orientation.isBlank() || orientation == "unspecified" || orientation == "behind") return

        val activityTheme = getActivityTheme(element)
        if (activityTheme == null || !isTranslucentTheme(activityTheme)) return

        translucentActivities.add(className)

        val message = "Fixing screen orientation is not supported with a translucent theme"
        val map = LintMap.Builder().put("className", className).build()
        val incident = Incident(ISSUE, element, context.getLocation(element), message, map)
        context.report(incident)
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != "android.app.Activity") return

        val callClass = node.getParentOfType(UClass::class.java) ?: return
        val className = callClass.qualifiedName ?: return

        val message = "Fixing screen orientation is not supported with a translucent theme"
        val map = LintMap.Builder().put("className", className).build()
        val incident = Incident(ISSUE, node, context.getLocation(node), message, map)
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.project.targetSdkVersion.apiLevel < MIN_TARGET_SDK) return false
        val className = map.get("className", String::class.java) ?: return false
        return translucentActivities.contains(className)
    }

    private fun getActivityTheme(activity: Element): String? {
        val theme = getAttributeValue(activity, "theme")
        return if (theme.isNotBlank()) normalizeTheme(theme) else applicationTheme
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        return translucentThemes.contains(theme) || theme.contains("Translucent")
    }

    private fun getActivityClassName(activity: Element): String? {
        val name = getAttributeValue(activity, "name")
        if (name.isBlank()) return null
        val pkg = activity.ownerDocument?.documentElement?.getAttribute("package") ?: return null
        return when {
            name.startsWith(".") -> "$pkg$name"
            name.contains(".") -> name
            else -> "$pkg.$name"
        }
    }

    private fun findParentStyle(element: Element): String? {
        var node = element.parentNode
        while (node != null) {
            if (node is Element && node.localName == "style") {
                return getAttributeValue(node, "name").takeIf { it.isNotBlank() }
            }
            node = node.parentNode
        }
        return null
    }

    private fun normalizeTheme(value: String): String {
        return value.trim().removePrefix("@style/").removePrefix("@android:style/")
    }

    private fun getAttributeValue(element: Element, localName: String): String {
        val namespaced = element.getAttributeNS(ANDROID_URI, localName)
        return if (namespaced.isNotEmpty()) namespaced else element.getAttribute(localName)
    }
}