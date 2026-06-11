package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class TranslationDetector : Detector(), ResourceFolderScanner, XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ExtraTranslation",
            briefDescription = "Extra translation without default locale string",
            explanation = """
                If a string appears in a specific language translation file but there is no corresponding string in the default locale, then this string is probably unused. These strings can lead to crashes if the string is looked up on any locale not providing a translation.
            """,
            category = Category.I18N,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val DEFAULT_LOCALE_FOLDER_NAME = "values"
    }

    private var defaultStrings: MutableSet<String> = mutableSetOf()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun checkFolder(context: ResourceContext, folderName: String) {
        if (folderName == DEFAULT_LOCALE_FOLDER_NAME) {
            defaultStrings.clear()
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("string", "string-array")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode("name") ?: return
        val name = nameAttr.value

        if (context.file.folderName == DEFAULT_LOCALE_FOLDER_NAME) {
            defaultStrings.add(name)
        } else {
            if (!defaultStrings.contains(name)) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "String '$name' is defined in a specific locale but not in the default locale"
                )
            }
        }
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Reset state for each file
        if (context.file.folderName == DEFAULT_LOCALE_FOLDER_NAME) {
            defaultStrings.clear()
        }
    }
}