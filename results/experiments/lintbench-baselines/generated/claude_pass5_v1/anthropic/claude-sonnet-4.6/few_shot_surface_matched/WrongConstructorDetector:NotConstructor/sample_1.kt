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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val NOT_CONSTRUCTOR = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors, but aren't.

                If you have a method that has the same name as the containing class, \
                the method will be a regular method, not a constructor. This is often \
                a mistake, especially if the method has no return type declared \
                (which in Java would make it a constructor, but in this context it \
                just has an implicit `void` return type).
            """,
            category = Category.CORRECTNESS,
            priority = 6,
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

    override fun createUastHandler(context: JavaContext): UastHandler {
        return UastHandler(context)
    }

    inner class UastHandler(private val context: JavaContext) : AbstractUastVisitor() {

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

            val methodName = method.name

            // Check if the method name matches the class name
            if (methodName != className) return

            // Check if the return type is void (which suggests it was meant to be a constructor)
            val returnType = method.returnType
            if (returnType == null || returnType == PsiType.VOID) {
                val location = context.getNameLocation(method)
                val message = "Method `$methodName` looks like a constructor but is a method; " +
                        "did you intend it to be a constructor?"
                context.report(NOT_CONSTRUCTOR, method, location, message)
            }
        }
    }

    // Required by SourceCodeScanner but logic is handled in createUastHandler
    override fun visitMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Not used; logic is in UastHandler.visitClass
    }
}

// Needed to avoid import error — UCallExpression used in visitMethod signature
import org.jetbrains.uast.UCallExpression