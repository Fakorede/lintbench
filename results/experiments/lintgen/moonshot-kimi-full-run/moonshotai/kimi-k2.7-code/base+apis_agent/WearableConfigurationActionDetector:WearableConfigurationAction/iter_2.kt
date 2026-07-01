package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private data class ServiceConfig(
        val location: Location,
        val value: String
    )

    private data class IntentFilterInfo(
        val actions: MutableSet<String> = mutableSetOf(),
        val categories: MutableSet<String> = mutableSetOf()
    )

    private val serviceConfigs = mutableListOf<ServiceConfig>()
    private val activityFilters = mutableListOf<IntentFilterInfo>()
    private var minSdk = 1

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.file.name != ANDROID_MANIFEST_XML) {
            return
        }

        val allElements = document.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val element = allElements.item(i) as? Element ?: continue
            if (localName(element) != TAG_SERVICE) {
                continue
            }
            val metaData = findConfigurationMetaData(element)
            if (metaData != null) {
                val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (value == WATCH_FACE_EDITOR_VALUE || value == EDITOR_ACTION_VALUE) {
                    serviceConfigs.add(ServiceConfig(context.getLocation(metaData), value))
                }
            }
        }

        for (i in 0 until allElements.length) {
            val element = allElements.item(i) as? Element ?: continue
            val tag = localName(element)
            if (tag != TAG_ACTIVITY && tag != TAG_ACTIVITY_ALIAS) {
                continue
            }
            for (child in childElements(element)) {
                if (localName(child) != TAG_INTENT_FILTER) {
                    continue
                }
                val filter = IntentFilterInfo()
                for (grandChild in childElements(child)) {
                    when (localName(grandChild)) {
                        TAG_ACTION -> {
                            val name = grandChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (name.isNotEmpty()) {
                                filter.actions.add(name)
                            }
                        }
                        TAG_CATEGORY -> {
                            val name = grandChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (name.isNotEmpty()) {
                                filter.categories.add(name)
                            }
                        }
                    }
                }
                activityFilters.add(filter)
            }
        }
    }

    override fun beforeCheckEachProject(context: Context) {
        serviceConfigs.clear()
        activityFilters.clear()
        minSdk = context.project.minSdk
    }

    override fun afterCheckEachProject(context: Context) {
        if (serviceConfigs.isEmpty()) {
            return
        }

        for (config in serviceConfigs) {
            val requiredAction = when (config.value) {
                WATCH_FACE_EDITOR_VALUE -> ACTION_WATCH_FACE_EDITOR
                EDITOR_ACTION_VALUE -> ACTION_EDITOR_ACTION
                else -> continue
            }
            val requiredCategory = when (config.value) {
                WATCH_FACE_EDITOR_VALUE ->
                    if (minSdk < 30) CATEGORY_WEARABLE_CONFIGURATION else null