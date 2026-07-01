package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlAttribute
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlDocument
import com.android.tools.lint.detector.api.XmlElement

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: XmlElement) {
        if (element == context.document.rootTag) {
            return
        }

        for (attribute in element.attributes) {
            if (attribute.isNamespaceDeclaration()) {
                val message = "Redundant namespace declaration: `${attribute.name}`"
                context.report(
                    REDUNDANT_NAMESPACE,
                    attribute,
                    context.getLocation(attribute),
                    message
                )
            }
        }
    }

    private fun XmlAttribute.isNamespaceDeclaration(): Boolean =
        name == "xmlns" || name.startsWith("xmlns:")

    companion object {
        val REDUNDANT_NAMESPACE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document element.
                Namespace declarations elsewhere in the document are typically accidental leftovers
                from copy/pasting XML from other files or documentation.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}