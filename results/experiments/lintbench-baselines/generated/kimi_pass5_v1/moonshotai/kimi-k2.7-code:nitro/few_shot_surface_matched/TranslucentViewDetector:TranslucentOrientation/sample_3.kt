package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val translucentStyles = mutableSetOf<String>()
    private val translucentActivityNames = mutableSetOf<String>()
    private var applicationTheme: String? = null

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_NAME)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        if (owner.tagName != TAG_ITEM) {
            return
        }

        val style = owner.parentNode as? Element ?: return
        if (style.tagName != TAG_STYLE) {
            return
        }

        val styleName = style.getAttribute(ATTR_NAME).takeIf { it.isNotBlank() } ?: return
        val itemName = attribute.value

        if (itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent") {
            val content = owner.textContent?.trim()
            if (content == "true" || content == "1" || content == "@bool/true" || content == "@android:bool/true") {
                translucentStyles.add(styleName)
            }
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, TAG_STYLE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> visitStyleElement(element)
            TAG_APPLICATION -> applicationTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).takeIf { it.isNotBlank() }
            TAG_ACTIVITY -> visitActivityElement(context, element)
        }
    }

    private fun visitStyleElement(element: Element) {
        val styleName = element.getAttribute(ATTR_NAME).takeIf { it.isNotBlank() } ?: return

        val items = element.getElementsByTagName(TAG_ITEM)
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val name = item.getAttribute(ATTR_NAME)
            if (name == "android:windowIsTranslucent" || name == "windowIsTranslucent") {
                val content = item.textContent?.trim()
                if (content == "true" || content == "1" || content == "@bool/true" || content == "@android:bool/true") {
                    translucentStyles.add(styleName)
                }
            }
        }

        val parent = element.getAttribute(ATTR_PARENT)
        if (parent.isNotBlank()) {
            val normalizedParent = extractStyleName(parent)
            if (translucentStyles.contains(normalizedParent) || parent.contains("Translucent", ignoreCase = true)) {
                translucentStyles.add(styleName)
            }
        }
    }

    private fun visitActivityElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        val packageName = context.document.documentElement.getAttributeNS(ANDROID_URI, ATTR_PACKAGE)
        val className = resolveActivityName(nameAttr.value, packageName)

        val themeValue = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
            .takeIf { it.isNotBlank() } ?: applicationTheme

        val translucent = themeValue != null && isTranslucentTheme(themeValue)
        if (translucent) {
            translucentActivityNames.add(className)
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION) ?: return
        if (translucent && isFixedOrientation(orientationAttr.value)) {
            val message = "Using a fixed screen orientation with a translucent theme is not " +
                "supported when targeting API $O_API_VERSION+"
            context.report(ISSUE, orientationAttr, context.getValueLocation(orientationAttr), message)
        }
    }

    override fun beforeCheckEachProject(context: Context) {
        translucentStyles.clear()
        translucentActivityNames.clear()
        applicationTheme = null
    }

    override fun appliesTo(context: Context, scope: Scope): Boolean {
        return scope == Scope.MANIFEST_SCOPE || scope == Scope.JAVA_FILE_SCOPE
    }

    override fun filterIncident(context: Context, incident: Incident, scope: Any?): Boolean {
        return context.project.targetSdkVersion.featureLevel >= O_API_VERSION
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_REQUESTED_ORIENTATION)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, CLASS_ACTIVITY)) {
            return
        }

        val containingClass = node.getParentOfType(UClass::class.java, true) ?: return
        val className = containingClass.qualifiedName ?: return

        if (translucentActivityNames.contains(className)) {
            val message = "Calling setRequestedOrientation in an Activity that uses a " +
                "translucent theme is not supported when targeting API $O_API_VERSION+"
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return orientation.isNotBlank() &&
            !orientation.equals("unspecified", ignoreCase = true) &&
            !orientation.equals("behind", ignoreCase = true)
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        val styleName = extractStyleName(theme)
        return translucentStyles.contains(styleName) ||
            theme.contains("Translucent", ignoreCase = true)
    }

    private fun extractStyleName(value: String): String {
        return value.substringAfterLast('/')
    }

    private fun resolveActivityName(name: String, packageName: String): String {
        return when {
            name.startsWith(".") -> packageName + name
            "." in name -> name
            else -> "$packageName.$name"
        }
    }

    companion object {
        private const val O_API_VERSION = 26
        private const val SET_REQUESTED_ORIENTATION = "setRequestedOrientation"
        private const val CLASS_ACTIVITY = "android.app.Activity"

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screen orientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported
                on apps with targetSdkVersion O or greater since there can be another activity
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind your
                translucent activity requests portrait. In this case the system can only honor one
                of the requests and currently prefers to honor the request from non-translucent
                activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your app
                if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_AND_JAVA_SCOPE
            ),
            androidSpecific = true
        )
    }
}