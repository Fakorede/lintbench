package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '<') {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Replace `<` with `&lt;`"
                )
                return
            }
            if (c == '&') {
                val semiColonIndex = value.indexOf(';', i + 1)
                if (semiColonIndex == -1 || semiColonIndex > i + 10) {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Replace `&` with `&amp;`"
                    )
                    return
                }
                val entity = value.substring(i + 1, semiColonIndex)
                val isValidEntity = entity == "amp" || entity == "lt" || entity == "gt" ||
                    entity == "quot" || entity == "apos" ||
                    entity.startsWith("#")
                if (!isValidEntity) {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Replace `&` with `&amp;`"
                    )
                    return
                }
                i = semiColonIndex
            }
            i++
        }
    }
}