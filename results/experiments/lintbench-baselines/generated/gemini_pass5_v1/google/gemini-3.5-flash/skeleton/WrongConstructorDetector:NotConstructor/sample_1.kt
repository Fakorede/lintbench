package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
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
                Are you sure this method is a constructor? It has the same name as the class \
                it is in, but it specifies a return type (even void). This means it is a regular \
                method, not a constructor, and will not be called when the class is instantiated. \
                This is usually a mistake where the 'void' keyword was accidentally added.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val psiMethod = node.javaPsi
                if (psiMethod.isConstructor) {
                    return
                }
                val containingClass = psiMethod.containingClass ?: return
                val className = containingClass.name ?: return
                if (psiMethod.name == className) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Method has the same name as its class, but is not a constructor (it specifies a return type)"
                    )
                }
            }
        }
}