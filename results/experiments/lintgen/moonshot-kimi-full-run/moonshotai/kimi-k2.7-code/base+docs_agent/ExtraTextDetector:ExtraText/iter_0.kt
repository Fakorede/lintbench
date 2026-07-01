package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Text

class ExtraTextDetector : Detector(), Detector.XmlScanner {

    override fun visitText(context: XmlContext, text: Text) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            return
        }

        val value = text.nodeValue ?: return
        if (value.isBlank()) {
            return
        }

        context.report(
            ISSUE,
            text,
            context.getLocation(text),
            "Extraneous text in resource file"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. Any XML text \
                content found in the file is likely accidental (and potentially dangerous if the \
                text resembles XML and the developer believes the text to be functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            Implementation(ExtraTextDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}