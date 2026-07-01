package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastLiteralUtils
import com.android.tools.lint.client.api.UElementHandler
import org.jetbrains.uast.UExpression

class DataBindingDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
                
                Characters like `<`, `>`, `&`, `"`, and `'` have special meaning in XML \
                and must be escaped when used in XML content or attributes.
                
                For example:
                - `<` should be escaped as `&lt;`
                - `>` should be escaped as `&gt;`
                - `&` should be escaped as `&amp;`
                - `"` should be escaped as `&quot;` (in attributes)
                - `'` should be escaped as `&apos;` (in attributes)
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val XML_SPECIAL_CHARS = setOf('<', '>', '&')
        
        // Methods that typically output to XML or HTML
        private val XML_OUTPUT_METHODS = setOf(
            "append", "write", "print", "println", "format"
        )
        
        private val XML_BUILDER_CLASSES = setOf(
            "StringBuilder", "StringBuffer", "PrintWriter", "Writer", 
            "OutputStream", "XmlSerializer"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = UastLiteralUtils.getValueIfStringLiteral(node) ?: return
                
                // Check if the string contains XML special characters
                val problematicChars = findProblematicChars(value)
                if (problematicChars.isEmpty()) return
                
                // Check if this string is being used in an XML context
                if (isUsedInXmlContext(node, context)) {
                    val charList = problematicChars.joinToString(", ") { "'$it'" }
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "String contains XML special characters ($charList) that should be escaped"
                    )
                }
            }
        }
    }

    private fun findProblematicChars(value: String): List<Char> {
        val found = mutableListOf<Char>()
        for (char in XML_SPECIAL_CHARS) {
            if (value.contains(char) && char !in found) {
                // For &, check if it's already an escape sequence
                if (char == '&') {
                    if (containsUnescapedAmpersand(value)) {
                        found.add(char)
                    }
                } else {
                    found.add(char)
                }
            }
        }
        return found
    }

    private fun containsUnescapedAmpersand(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            if (value[i] == '&') {
                // Check if this is already an escape sequence
                val rest = value.substring(i)
                if (!rest.startsWith("&amp;") && 
                    !rest.startsWith("&lt;") && 
                    !rest.startsWith("&gt;") && 
                    !rest.startsWith("&quot;") && 
                    !rest.startsWith("&apos;") &&
                    !isNumericEntity(rest)) {
                    return true
                }
            }
            i++
        }
        return false
    }

    private fun isNumericEntity(str: String): Boolean {
        if (!str.startsWith("&#")) return false
        val end = str.indexOf(';')
        if (end == -1) return false
        val entity = str.substring(2, end)
        return if (entity.startsWith("x") || entity.startsWith("X")) {
            entity.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        } else {
            entity.all { it.isDigit() }
        }
    }

    private fun isUsedInXmlContext(node: ULiteralExpression, context: JavaContext): Boolean {
        // Check parent expressions to determine if this is XML context
        var parent = node.uastParent
        
        while (parent != null) {
            if (parent is UCallExpression) {
                val methodName = parent.methodName ?: ""
                
                // Check if it's a method call that suggests XML output
                if (methodName in XML_OUTPUT_METHODS) {
                    val receiver = parent.receiver
                    if (receiver != null) {
                        val receiverType = receiver.getExpressionType()?.canonicalText ?: ""
                        for (xmlClass in XML_BUILDER_CLASSES) {
                            if (receiverType.contains(xmlClass)) {
                                return true
                            }
                        }
                    }
                }
                
                // Check for XML-specific methods
                if (methodName.contains("xml", ignoreCase = true) || 
                    methodName.contains("html", ignoreCase = true) ||
                    methodName.contains("Xml", ignoreCase = false) ||
                    methodName.contains("Html", ignoreCase = false)) {
                    return true
                }
                
                // Check method parameters for XML-related names
                val resolvedMethod = parent.resolve()
                if (resolvedMethod != null) {
                    val containingClass = resolvedMethod.containingClass
                    val className = containingClass?.qualifiedName ?: ""
                    if (className.contains("Xml") || className.contains("Html") || 
                        className.contains("xml") || className.contains("html")) {
                        return true
                    }
                }
            }
            
            parent = parent.uastParent
        }
        
        return false
    }
}