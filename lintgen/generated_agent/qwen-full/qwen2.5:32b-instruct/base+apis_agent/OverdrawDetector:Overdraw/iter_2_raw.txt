package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;

import java.util.Collections;
import java.util.List;

public class OverdrawDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (node != null && node.getValueArgumentCount() > 0) {
            // Check for setContentView(R.layout.some_layout)
            final String layoutId = getLayoutId(node);
            if (layoutId != null) {
                checkLayout(context, layoutId);
            }
        }
    }

    private void checkLayout(JavaContext context, String layoutId) {
        XmlContext xmlContext = context.getDriver().getXmlContext(layoutId);
        if (xmlContext == null) return;

        Element rootElement = xmlContext.getDocument().getDocumentElement();

        // Check for background drawable on the root view
        Attr backgroundAttr = rootElement.getAttributeNode("android:background");
        if (backgroundAttr != null && !isThemeBackgroundNull(xmlContext)) {
            context.report(ISSUE, node.getJavaPsi(), context.getLocation(node),
                    "Setting a background drawable on the root view can cause overdraw. Consider using a custom theme with a null background.");
        }
    }

    private boolean isThemeBackgroundNull(XmlContext xmlContext) {
        // Check if the current theme has its background set to null
        return false;  // Placeholder for actual logic
    }

    private String getLayoutId(UCallExpression node) {
        // Extract layout id from setContentView call
        return null;  // Placeholder for actual logic
    }
}