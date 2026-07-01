package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        MethodVisitor(context)

    private inner class MethodVisitor(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.isConstructor) return
            if (node.hasModifierProperty(PsiModifier.STATIC)) return
            if (node.containingClass?.isInterface == true) return

            val returnType = node.returnType ?: return
            val className = node.containingClass?.name ?: return

            val methodName = node.name ?: return
            if (methodName != className) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Method looks like a constructor but has return type '$returnType'; " +
                        "remove the return type if a constructor was intended."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This method has the same name as its containing class and declares a return type, \
                so it is treated as an ordinary method rather than a constructor. If it was meant \
                to be a constructor, remove the return type.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}