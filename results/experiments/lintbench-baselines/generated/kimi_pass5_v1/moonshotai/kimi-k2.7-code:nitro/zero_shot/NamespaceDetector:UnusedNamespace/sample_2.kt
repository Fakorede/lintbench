package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.concurrent.ConcurrentHashMap

class NamespaceDetector : Detector(), Detector.XmlScanner {

    private data class FileState(
        val declarations: MutableMap<String, MutableList<Attr>> = mutableMapOf(),
        val prefixedUses: MutableSet<String> = mutableSetOf(),
        val defaultNsUses: MutableSet<String> = mutableSetOf()
    )

    private val fileStates = ConcurrentHashMap<String, FileState>()

    override fun getApplicableElements(): Collection<String> =
        listOf(XmlScannerConstants.NODE_ALL)

    override fun getApplicableAttributes(): Collection<String> =
        listOf(XmlScannerConstants.ATTR_ALL)

    override fun beforeCheckFile(context: Context) {
        val path = context.file?.path ?: return
        fileStates[path] = FileState()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val state = getState(context) ?: return
        val prefix = element.prefix
        if (!prefix.isNullOrEmpty()) {
            state.prefixedUses.add(prefix)
        } else {
            val namespaceUri = element.namespaceURI
            if (!namespaceUri.isNullOrEmpty()) {
                state.defaultNsUses.add(namespaceUri)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val state = getState(context) ?: return
        when {
            attribute.prefix == "xmlns" -> {
                val declaredPrefix = attribute.localName ?: ""
                state.declarations.getOrPut(declaredPrefix) { mutableListOf() }
                    .add(attribute)
            }
            attribute.name == "xmlns" -> {
                state.declarations.getOrPut("") { mutableListOf() }
                    .add(attribute)
            }
            !attribute.prefix.isNullOrEmpty() && attribute.prefix != "xml" -> {
                state.prefixedUses.add(attribute.prefix)
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val path = context.file?.path ?: return
        val state = fileStates.remove(path) ?: return
        val xmlContext = context as XmlContext

        for ((prefix, attributes) in state.declarations) {
            val used = if (prefix.isEmpty()) {
                val declaredUri = attributes.firstOrNull()?.value
                declaredUri != null && state.defaultNsUses.contains(declaredUri)
            } else {
                state.prefixedUses.contains(prefix)
            }

            if (used) continue

            for (attr in attributes) {
                val location = xmlContext.getLocation(attr)
                val message = if (prefix.isEmpty()) {
                    "Unused default namespace `${attr.value}`"
                } else {
                    "Unused namespace `$prefix`"
                }
                xmlContext.report(ISSUE, attr, location, message)
            }
        }
    }

    private fun getState(context: XmlContext): FileState? {
        val path = context.file?.path ?: return null
        return fileStates.getOrPut(path) { FileState() }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is
                not necessary.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 2,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}