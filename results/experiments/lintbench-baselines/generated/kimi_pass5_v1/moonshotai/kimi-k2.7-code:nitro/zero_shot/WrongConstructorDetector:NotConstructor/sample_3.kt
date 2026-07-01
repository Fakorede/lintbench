package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) {
                    return
                }

                val methodName = node.name ?: return
                val className = getEnclosingClassName(node) ?: return
                if (methodName != className) {
                    return
                }

                if (node.returnType == null) {
                    return
                }

                val location: Location = context.getLocation(node)
                context.report(
                    NOT_CONSTRUCTOR,
                    node,
                    location,
                    "This method has the same name as the surrounding class and declares a return type, so it is not a constructor."
                )
            }
        }
    }

    private fun getEnclosingClassName(node: UMethod): String? {
        var parent: UElement? = node.uastParent
        while (parent != null) {
            if (parent is UClass) {
                return parent.name
            }
            parent = parent.uastParent
        }
        return null
    }

    companion object {
        val NOT_CONSTRUCTOR = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors, but aren't.
                This typically happens when a method is given the same name as its surrounding class and also declares a return type.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}