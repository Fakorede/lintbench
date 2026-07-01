package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.TAG_VIEW
import com.android.SdkConstants.TAG_VIEW_STUB
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import com.android.utils.XmlUtils
import com.intellij.psi.*
import org.jetbrains.uast.*
import java.util.*

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val REQUIRE_VIEW_BY_ID = "requireViewById"

        @JvmField
        val FIND_VIEW_BY_ID_CAST = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = "...",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE),
            androidSpecific = true
        )
    }

    // XML
    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_ID)

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val url = ResourceUrl.parse(value) ?: return
        if (!url.isValid || url.type != ResourceType.ID || url.isFramework) return
        val element = attribute.ownerElement
        val tag = element.tagName
        val viewClass = if (tag == TAG_VIEW) element.getAttributeNS(ANDROID_URI, "class") else tag
        if (viewClass.isNullOrEmpty()) return
        val map = context.getProject().getReferenceMap() ?? 
        // Need store in per-run storage.
    }
}