package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

class AppCompatCustomViewDetector : Detector(), Detector.ClassScanner {

    override fun getApplicableCallOwners(): List<String>? = null

    override fun checkSameOwner(): Boolean = false

    override fun visitClass(context: ClassContext, classNode: ClassNode) {
        val superName = classNode.superName ?: return
        val replacement = WIDGET_TO_APPCOMPAT[superName] ?: return
        val className = classNode.name ?: return
        if (className.startsWith(APPCOMPAT_PACKAGE_PREFIX)) {
            return
        }

        val widgetSimpleName = superName.substringAfterLast('/')
        val appCompatSimpleName = replacement.substringAfterLast('/')
        val message = "This custom view should extend `androidx.appcompat.widget.$appCompatSimpleName` instead of `android.widget.$widgetSimpleName`"
        context.report(ISSUE, classNode, context.getLocation(classNode), message)
    }

    override fun visitMethod(context: ClassContext, methodNode: MethodNode) {}
    override fun visitField(context: ClassContext, fieldNode: FieldNode) {}
    override fun visitInstruction(
        context: ClassContext,
        methodNode: MethodNode,
        instruction: AbstractInsnNode
    ) {}

    companion object {
        private const val APPCOMPAT_PACKAGE_PREFIX = "androidx/appcompat/widget/"

        private val WIDGET_TO_APPCOMPAT: Map<String, String> = mapOf(
            "android/widget/TextView" to "androidx/appcompat/widget/AppCompatTextView",
            "android/widget/EditText" to "androidx/appcompat/widget/AppCompatEditText",
            "android/widget/AutoCompleteTextView" to "androidx/appcompat/widget/AppCompatAutoCompleteTextView",
            "android/widget/MultiAutoCompleteTextView" to "androidx/appcompat/widget/AppCompatMultiAutoCompleteTextView",
            "android/widget/Button" to "androidx/appcompat/widget/AppCompatButton",
            "android/widget/ImageButton" to "androidx/appcompat/widget/AppCompatImageButton",
            "android/widget/ImageView" to "androidx/appcompat/widget/AppCompatImageView",
            "android/widget/CheckBox" to "androidx/appcompat/widget/AppCompatCheckBox",
            "android/widget/RadioButton" to "androidx/appcompat/widget/AppCompatRadioButton",
            "android/widget/RadioGroup" to "androidx/appcompat/widget/AppCompatRadioGroup",
            "android/widget/CheckedTextView" to "androidx/appcompat/widget/AppCompatCheckedTextView",
            "android/widget/Spinner" to "androidx/appcompat/widget/AppCompatSpinner",
            "android/widget/SeekBar" to "androidx/appcompat/widget/AppCompatSeekBar",
            "android/widget/ProgressBar" to "androidx/appcompat/widget/AppCompatProgressBar",
            "android/widget/Switch" to "androidx/appcompat/widget/SwitchCompat",
            "android/widget/Toolbar" to "androidx/appcompat/widget/Toolbar",
            "android/widget/RatingBar" to "androidx/appcompat/widget/AppCompatRatingBar",
            "android/widget/ToggleButton" to "androidx/appcompat/widget/AppCompatToggleButton"
        )

        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            EnumSet.of(Scope.CLASS_FILE_SCOPE)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            "AppCompatCustomView",
            "Appcompat Custom Widgets",
            """
                In order to support features such as tinting, the appcompat library will automatically load special appcompat replacements for the builtin widgets. However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should instead extend one of the delegate classes in `androidx.appcompat.widget.AppCompatTextView`.
            """,
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
        )
    }
}