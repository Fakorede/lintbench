package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongConstructorDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                A method whose name matches the containing class but declares a return type is
                not a constructor. Constructors have no return type. This typically happens when
                a return type is accidentally added to what was intended to be a constructor.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val methodName = node.name ?: return
                val containingClass = node.containingClass ?: return
                if (containingClass.isInterface || containingClass.isAnnotationType) return

                val className = containingClass.name ?: return
                if (methodName == className && node.returnType != null) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method '$methodName' looks like it was intended to be a constructor, " +
                                "but it declares a return type and is therefore not a constructor."
                    )
                }
            }
        }
}