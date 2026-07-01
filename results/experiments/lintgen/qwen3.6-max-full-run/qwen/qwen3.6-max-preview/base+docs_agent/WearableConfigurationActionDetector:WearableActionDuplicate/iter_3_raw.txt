package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, with the value WATCH_FACE_EDITOR, there should be an activity in the same package, which has an intent filter for WATCH_FACE_EDITOR (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val minSdk = context.mainProject.minSdkVersion?.apiLevel ?: 1

        val appNodes = element.getElementsByTagName(TAG_APPLICATION)
        if (appNodes.length == 0) return
        val app = appNodes.item(0) as Element

        var hasServiceWithConfig = false
        val editorActivities = mutableListOf<Element>()

        val children = app.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element

            when (el.tagName) {
                TAG_SERVICE -> {
                    val metaNodes = el.getElementsByTagName(TAG_META_DATA)
                    for (j in 0 until metaNodes.length) {
                        val meta = metaNodes.item(j) as Element
                        val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                        if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                            hasServiceWithConfig = true
                            break
                        }
                    }
                }
                TAG_ACTIVITY -> {
                    val filterNodes = el.getElementsByTagName(TAG_INTENT_FILTER)
                    for (j in 0 until filterNodes.length) {
                        val filter = filterNodes.item(j) as Element
                        var hasAction = false
                        var hasCategory = minSdk >= 30

                        val fChildren = filter.childNodes
                        for (k in 0 until fChildren.length) {
                            val fc = fChildren.item(k)
                            if (fc.nodeType == Node.ELEMENT_NODE) {
                                val fcEl = fc as Element
                                val attrName = fcEl.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (fcEl.tagName == TAG_ACTION && attrName == "WATCH_FACE_EDITOR") {
                                    hasAction = true
                                } else if (fcEl.tagName == TAG_CATEGORY &&
                                    attrName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
                                ) {
                                    hasCategory = true
                                }
                            }
                        }

                        if (hasAction && hasCategory) {
                            editorActivities.add(el)
                            break
                        }
                    }
                }
            }
        }

        if (hasServiceWithConfig && editorActivities.size > 1) {
            context.report(
                ISSUE,
                editorActivities[1],
                context.getLocation(editorActivities[1]),
                "Duplicate watch face configuration activities found"
            )
        }
    }
}