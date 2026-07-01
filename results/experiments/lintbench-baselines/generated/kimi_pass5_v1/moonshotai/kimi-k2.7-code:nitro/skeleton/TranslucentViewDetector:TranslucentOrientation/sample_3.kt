package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val styleParents = mutableMapOf<String, String?>()
    private val styleTranslucentItems = mutableMapOf<String, Boolean>()
    private var applicationTheme: String? = null

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private const val ATTR_NAME = "name"
        private const val ATTR_PARENT = "parent"
        private const val ATTR_THEME = "theme"
        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val ATTR_WINDOW_SWIPE_TO_DIM = "windowSwipeToDim"

        private const val ANDROID_O_API_LEVEL = 26

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FILE, Scope.JAVA_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme is not supported
                on apps whose `targetSdkVersion` is O (API 26) or greater. If another activity
                visible behind your translucent activity requests a different orientation, the
                system can only honor one of the requests and currently prefers the non-translucent
                activity. Devices running Android O or greater will throw an exception when this
                state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        styleParents.clear()
        styleTranslucentItems.clear()
        applicationTheme = null
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun getApplicableElements(): Collection<String>? =
        listOf("application", "activity", "style")

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.MANIFEST || folderType == ResourceFolderType.VALUES

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No-op: attributes are processed via visitElement.
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            if (element.tagName == "style") {
                collectStyle(element)
            }
            return
        }

        when (element.tagName) {
            "application" -> {
                applicationTheme =
                    normalizeStyleReference(element.getAttributeNS(ANDROID_URI, ATTR_THEME))
            }
            "activity" -> {
                checkActivity(context, element)
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.getMainProject().manifestTargetSdkVersion
        if (targetSdk != -1 && targetSdk < ANDROID_O_API_LEVEL) {
            return false
        }

        val theme = map.getString(ATTR_THEME) ?: return false
        return isTranslucentStyle(theme)
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Not used; the problematic declaration is in the manifest.
    }

    private fun collectStyle(element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeUnless { it.isBlank() }
            ?: return

        val explicitParent = element.getAttributeNS(ANDROID_URI, ATTR_PARENT)
            .takeUnless { it.isBlank() }
            ?.let { normalizeStyleReference(it) }

        val implicitParent = if (name.contains('.')) name.substringBeforeLast('.') else null

        styleParents[name] = explicitParent ?: implicitParent

        var translucent = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != "item") continue

            val itemName = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val itemValue = child.textContent?.trim() ?: ""
            if (isTranslucentItem(itemName) && itemValue == "true") {
                translucent = true
                break
            }
        }

        if (translucent) {
            styleTranslucentItems[name] = true
        }
    }

    private fun checkActivity(context: XmlContext, element: Element) {
        val screenAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val screenValue = screenAttr?.value
        if (screenValue.isNullOrBlank() || screenValue == "unspecified") {
            return
        }

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
        val themeRef = normalizeStyleReference(themeAttr?.value) ?: applicationTheme
        if (themeRef.isNullOrBlank()) {
            return
        }

        val location = screenAttr?.let { context.getLocation(it) } ?: context.getLocation(element)
        val message = "Mixing ${ATTR_SCREEN_ORIENTATION} and translucency (theme: $themeRef)"
        val map = LintMap.Builder()
            .putString(ATTR_THEME, themeRef)
            .putString(ATTR_SCREEN_ORIENTATION, screenValue)
            .build()

        context.report(Incident(ISSUE, location, message, map))
    }

    private fun isTranslucentStyle(name: String?): Boolean {
        if (name == null) return false

        val seen = mutableSetOf<String>()
        var current: String? = name
        while (current != null && seen.add(current)) {
            if (styleTranslucentItems[current] == true) return true
            current = styleParents[current]
        }
        return false
    }

    private fun isTranslucentItem(name: String): Boolean {
        return name == ATTR_WINDOW_IS_TRANSLUCENT ||
            name == ATTR_WINDOW_SWIPE_TO_DIM ||
            name == "android:$ATTR_WINDOW_IS_TRANSLUCENT" ||
            name == "android:$ATTR_WINDOW_SWIPE_TO_DIM"
    }

    private fun normalizeStyleReference(value: String?): String? {
        val v = value?.trim() ?: return null
        return when {
            v.startsWith("@style/") -> v.substring(7)
            v.startsWith("@android:style/") -> v.substring(15)
            else -> null
        }
    }
}