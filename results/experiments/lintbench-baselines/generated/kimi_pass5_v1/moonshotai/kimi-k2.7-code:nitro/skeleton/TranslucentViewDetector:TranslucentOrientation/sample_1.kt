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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastUtils
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val translucentStyles = mutableSetOf<String>()

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ACTIVITY = "activity"
        private const val ITEM = "item"
        private const val STYLE = "style"
        private const val NAME = "name"
        private const val THEME = "theme"
        private const val SCREEN_ORIENTATION = "screenOrientation"
        private const val WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val MIN_API = 26

        private const val MESSAGE =
            "Using a fixed screen orientation with a translucent theme is not supported " +
                "on apps with a targetSdkVersion of O or higher"

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = "Specifying a fixed screen orientation with a translucent theme is " +
                "not supported on apps with a targetSdkVersion of O (API 26) or greater, " +
                "because another activity visible behind the translucent activity may request a " +
                "conflicting orientation. On devices running Android O and higher this will " +
                "throw an exception.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf(SCREEN_ORIENTATION)

    override fun getApplicableElements(): Collection<String>? = listOf(ITEM)

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName != SCREEN_ORIENTATION) return

        val activity = attribute.ownerElement ?: return
        if (activity.tagName != ACTIVITY) return

        val orientationValue = activity.getAttributeNS(ANDROID_URI, SCREEN_ORIENTATION)
        if (!isFixedOrientation(orientationValue)) return

        val theme = activity.getAttributeNS(ANDROID_URI, THEME)
        if (theme.isBlank()) return

        if (isTranslucentTheme(theme)) {
            val name = activity.getAttributeNS(ANDROID_URI, NAME)
            val displayName = if (name.isNotBlank()) name else "this activity"
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "$MESSAGE ($displayName)",
            )
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != ITEM) return

        val itemName = element.getAttribute(NAME)
        val localName = itemName.substringAfterLast(':', "")
        if (localName != WINDOW_IS_TRANSLUCENT) return

        val value = element.textContent?.trim() ?: return
        if (value != "true") return

        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != STYLE) return

        val styleName = parent.getAttribute(NAME)
        if (styleName.isNotBlank()) {
            translucentStyles.add(styleName)
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.mainProject.targetSdkVersion?.featureLevel
            ?: context.project.targetSdkVersion?.featureLevel
            ?: Int.MAX_VALUE
        return targetSdk >= MIN_API
    }

    override fun getApplicableMethodNames(): List<String>? =
        listOf("setTheme", "setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (node.methodName != "setTheme") return

        val styleName = resolveStyleName(node) ?: return
        if (!isTranslucentStyleName(styleName)) return

        val containingMethod = UastUtils.getParentOfType(
            node,
            UMethod::class.java,
            false,
        ) ?: return

        val orientationCalls = containingMethod.uastBody.findCalls("setRequestedOrientation")
        if (orientationCalls.isNotEmpty()) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE,
            )
        }
    }

    private fun resolveStyleName(call: UCallExpression): String? {
        val arg = call.valueArguments.getOrNull(0) ?: return null
        return when (arg) {
            is USimpleNameReferenceExpression -> (arg.resolve() as? PsiField)?.name
            is UQualifiedReferenceExpression -> {
                val selector = arg.selector
                if (selector is USimpleNameReferenceExpression) {
                    (selector.resolve() as? PsiField)?.name
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun isFixedOrientation(value: String): Boolean {
        return value.isNotBlank() &&
            value != "unspecified" &&
            value != "behind" &&
            value != "fullUser"
    }

    private fun isTranslucentTheme(themeValue: String): Boolean {
        val styleName = parseStyleName(themeValue) ?: return false
        if (styleName in translucentStyles) return true
        if (isTranslucentStyleName(styleName)) return true
        return false
    }

    private fun isTranslucentStyleName(name: String): Boolean {
        val normalized = name.lowercase().replace("_", ".")
        return normalized.contains("translucent")
    }

    private fun parseStyleName(themeValue: String): String? {
        if (themeValue.isBlank()) return null
        var name = themeValue.substringAfterLast('/')
        if (name.startsWith("@")) name = name.substring(1)
        if (name.contains(":")) name = name.substringAfterLast(":")
        return name
    }

    private fun UElement?.findCalls(name: String): List<UCallExpression> {
        if (this == null) return emptyList()
        val result = mutableListOf<UCallExpression>()
        if (this is UCallExpression && this.methodName == name) {
            result.add(this)
        }
        for (child in uastChildren) {
            result.addAll(child.findCalls(name))
        }
        return result
    }
}