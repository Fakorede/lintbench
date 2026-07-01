package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can
                cause many versions of `aapt`, the resource packaging tool, to crash. To work
                around this, declare the id explicitly with
                `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private val DYNAMIC_ID_REGEX = Regex("@\\+id/(.+)")
    }

    private val explicitIds = mutableSetOf<String>()
    private val candidates = mutableListOf<Candidate>()

    private data class Candidate(val location: Location, val name: String, val message: String)

    override fun beforeCheckRootProject(context: Context) {
        explicitIds.clear()
        candidates.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = listOf("item")

    override fun getApplicableAttributes(): Collection<String>? = listOf("name")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "item") {
            return
        }

        val type = element.getAttribute("type")
        val name = element.getAttribute("name")
        if (type == "id" && name.isNotBlank()) {
            explicitIds.add(name)
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        if (attribute.value != "android:id") {
            return
        }

        val element = attribute.ownerElement ?: return
        if (element.tagName != "item") {
            return
        }

        val parent = element.parentNode as? org.w3c.dom.Element ?: return
        if (parent.tagName != "style") {
            return
        }

        val text = element.textContent?.trim() ?: return
        val match = DYNAMIC_ID_REGEX.matchEntire(text) ?: return
        val idName = match.groupValues[1]
        if (idName.isBlank()) {
            return
        }

        val message = "Dynamically generated id `$idName` in a style can cause AAPT to crash; " +
            "declare it explicitly with `<item type=\"id\" name=\"$idName\" />`"
        candidates.add(Candidate(context.getLocation(attribute), idName, message))
    }

    override fun afterCheckRootProject(context: Context) {
        for (candidate in candidates) {
            if (candidate.name !in explicitIds) {
                context.report(ISSUE, candidate.location, candidate.message)
            }
        }
    }
}