package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors, but aren't.

                If you have a method that has the same name as the containing class, \
                then it is possible that you intended it to be a constructor but \
                accidentally included a return type, which turns it into a regular method \
                instead. This is usually a typo or mistake.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UastVisitor {
        return WrongConstructorVisitor(context)
    }

    private class WrongConstructorVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitClass(node: UClass): Boolean {
            val className = node.name ?: return false
            for (method in node.methods) {
                visitMethod(method, className)
            }
            return false
        }

        private fun visitMethod(method: UMethod, className: String) {
            // Skip actual constructors
            if (method.isConstructor) return

            // Check if the method name matches the class name
            val methodName = method.name
            if (methodName == className) {
                // This method has the same name as the containing class but has a return type,
                // which means it's not a constructor — likely a mistake.
                val location = context.getNameLocation(method)
                val message = "Method `$methodName` looks like a constructor but has a return type; " +
                        "it will be interpreted as a method, not a constructor"
                context.report(ISSUE, method, location, message)
            }
        }
    }

    fun visitMethod(context: JavaContext, node: UMethod, method: PsiMethod) {
        // Required by SourceCodeScanner interface — handled via UastHandler above
    }
}