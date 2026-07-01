package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {
    override fun getApplicableElements(): Collection<String> = listOf(MOTION_LAYOUT)
    override fun visitElement(context: XmlContext, element:  Element) {
        val description = element.getAttributeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)
        if (description.isBlank()) {
            context.report(ISSUE, element, context.getElementNameLocation(element), "$ATTR_LAYOUT_DESCRIPTION must specify a scene file")
            return
        }
        if (!description.startsWith(MOTION_SCENE_PREFIX)) {
            context.report(...)
            return
        }
        val fileName = description.substringAfter(MOTION_SCENE_PREFIX)
        if (!context.project.resourceFolders.any { File(it, "xml/$fileName.xml").exists() }) {
            context.report(...)
        }
    }
    companion object {
        const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        const val MOTION_SCENE_PREFIX = "@xml/"
        private val IMPLEMENTATION = Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE = Issue.create(...)
    }
}