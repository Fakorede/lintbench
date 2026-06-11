package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class OverdrawDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".",
            Category.PERFORMANCE,
            6,
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
    public void visitMethodCall(JavaContext context, UCallExpression node) {
        if (node.getValueArgumentCount() > 0 && node.getValueArgument(0).getPsi() instanceof PsiElement) {
            String layoutId = ((PsiElement) node.getValueArgument(0).getPsi()).getText();
            checkLayout(context, layoutId);
        }
    }

    private void checkLayout(JavaContext context, String layoutId) {
        XmlContext xmlContext = context.getDriver().createXmlContext(layoutId);
        if (xmlContext != null) {
            visitDocument(xmlContext, xmlContext.getDocument());
        }
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("View");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr backgroundAttr = element.getAttributeNode("android:background");
        if (backgroundAttr != null && !element.getTagName().equals("root")) {
            reportOverdraw(context, element);
        }
    }

    private void reportOverdraw(XmlContext context, Element element) {
        context.report(
                ISSUE,
                context.getLocation(element),
                "Setting a background drawable on this view may cause overdraw. Consider using a custom theme with null background instead."
        );
    }
}