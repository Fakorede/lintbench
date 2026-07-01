package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttribute("name") == "android:id") {
            val value = element.textContent?.trim() ?: return
            if (value.startsWith("@+id/")) {
                val fix = fix()
                    .name("Change to @id/")
                    .replace()
                    .text(value)
                    .with(value.replace("@+id/", "@id/"))
                    .autoFix()
                    .build()

                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Avoid defining `android:id` with a dynamically generated id (`@+id/`) in styles; this can cause AAPT to crash. Declare the id explicitly instead.",
                    fix
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with `<item type="id" name="..." />` instead.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}