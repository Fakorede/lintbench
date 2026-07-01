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
                This check catches methods that look like they were intended to be constructors, \
                but aren't.

                If you define a method with the same name as the containing class, it may look \
                like a constructor but it is actually a regular method. This is typically a mistake \
                where the developer forgot to remove the return type, or misspelled the constructor \
                name.
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

    override fun createUastHandler(context: JavaContext): NotConstructorVisitor {
        return NotConstructorVisitor(context)
    }

    inner class NotConstructorVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitClass(node: UClass): Boolean {
            val className = node.name ?: return false

            for (method in node.methods) {
                visitMethod(context, method, className)
            }

            return false
        }

        private fun visitMethod(context: JavaContext, method: UMethod, className: String) {
            // Skip actual constructors
            if (method.isConstructor) return

            // Check if the method name matches the class name
            if (method.name != className) return

            // This method has the same name as the class but has a return type,
            // so it looks like a constructor but isn't one.
            val location = context.getNameLocation(method)
            context.report(
                ISSUE,
                method,
                location,
                "Method `${method.name}` looks like a constructor but has a return type; " +
                    "did you mean to be a constructor? (Or did you intend to name this method " +
                    "differently?)"
            )
        }
    }

    fun visitMethod(context: JavaContext, node: UMethod, method: PsiMethod) {
        // Required by SourceCodeScanner interface usage pattern but logic is handled in visitor
    }
}