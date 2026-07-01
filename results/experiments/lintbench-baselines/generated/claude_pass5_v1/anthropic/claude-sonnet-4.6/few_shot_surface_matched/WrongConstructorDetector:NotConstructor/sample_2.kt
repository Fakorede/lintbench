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

                If you define a method with the same name as the containing class, it may have \
                been intended as a constructor, but since it has a return type it is treated as \
                a regular method. This is a common mistake, especially when converting Java code \
                or when a class is renamed without updating the constructor.
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

            // Check if the method name matches the class name
            val methodName = method.name
            if (methodName != className) return

            // If the method has the same name as the class but has a return type,
            // it looks like it was intended to be a constructor
            val returnType = method.returnType ?: return

            // void return type methods with class name are especially suspicious
            val message = if (returnType.canonicalText == "void") {
                "Method '${methodName}' has the same name as its containing class, which looks " +
                    "like an attempt to write a constructor, but it has a void return type. " +
                    "Did you mean to create a constructor?"
            } else {
                "Method '${methodName}' has the same name as its containing class, which looks " +
                    "like an attempt to write a constructor, but constructors do not have return " +
                    "types. Did you mean to create a constructor?"
            }

            val location = context.getNameLocation(method)
            context.report(ISSUE, method, location, message)
        }
    }

    // Required by SourceCodeScanner - this is called for method calls but we use
    // the UAST visitor approach via createUastHandler instead
    fun visitMethod(context: JavaContext, node: org.jetbrains.uast.UCallExpression, method: PsiMethod) {
        // Not used - we use the UAST visitor approach
    }
}