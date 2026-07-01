package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.Locale

class TranslucentViewDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION) ?: return
        val orientation = orientationAttr.value
        if (orientation == "unspecified") {
            return
        }

        var themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
        if (themeAttr == null) {
            val parent = element.parentNode as? Element
            if (parent != null && parent.tagName == TAG_APPLICATION) {
                themeAttr = parent.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
            }
        }

        val themeUrl = themeAttr?.value ?: return

        if (checkTranslucency(context, themeUrl)) {
            context.report(
                ISSUE,
                orientationAttr,
                context.getLocation(orientationAttr),
                "Activity is using a translucent theme but has a fixed screen orientation"
            )
        }
    }

    private fun checkTranslucency(context: XmlContext, themeUrl: String): Boolean {
        val lower = themeUrl.toLowerCase(Locale.US)
        if (lower.contains("translucent") || lower.contains("dialog") || lower.contains("floating")) {
            return true
        }

        val resourceUrl = ResourceUrl.parse(themeUrl) ?: return false
        val client = context.client
        val project = context.project
        val repository = client.getResources(project, ResourceRepositoryScope.ALL) ?: return false

        val namespace = if (resourceUrl.isFramework) ResourceNamespace.ANDROID else ResourceNamespace.RES_AUTO
        return isTranslucentStyle(repository, namespace, resourceUrl.name)
    }

    private fun isTranslucentStyle(
        repository: ResourceRepository,
        namespace: ResourceNamespace,
        styleName: String
    ): Boolean {
        val visited = mutableSetOf<String>()
        return isTranslucentStyleInternal(repository, namespace, styleName, visited)
    }

    private fun isTranslucentStyleInternal(
        repository: ResourceRepository,
        namespace: ResourceNamespace,
        styleName: String,
        visited: MutableSetOf<String>
    ): Boolean {
        val key = "$namespace:$styleName"
        if (!visited.add(key)) return false

        val resources = repository.getResources(namespace, ResourceType.STYLE, styleName)
        if (resources.isEmpty()) {
            val lower = styleName.toLowerCase(Locale.US)
            return lower.contains("translucent") || lower.contains("dialog") || lower.contains("floating")
        }

        for (resource in resources) {
            val styleValue = resource.resourceValue as? StyleResourceValue ?: continue
            
            val isTranslucentItem = styleValue.getItem(ResourceNamespace.ANDROID, "windowIsTranslucent")?.value?.toBoolean() == true
            val isFloatingItem = styleValue.getItem(ResourceNamespace.ANDROID, "windowIsFloating")?.value?.toBoolean() == true
            val isSwipeToDismiss = styleValue.getItem(ResourceNamespace.ANDROID, "windowSwipeToDismiss")?.value?.toBoolean() == true

            if (isTranslucentItem || isFloatingItem || isSwipeToDismiss) {
                return true
            }

            val parent = styleValue.parentStyleName
            if (parent != null) {
                val parentUrl = ResourceUrl.parse(parent) ?: continue
                val parentNamespace = parentUrl.namespace ?: namespace
                if (isTranslucentStyleInternal(repository, parentNamespace, parentUrl.name, visited)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity request portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """,
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