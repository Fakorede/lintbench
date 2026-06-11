package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Using `showAsAction=always` in menu XML or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide.",
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead. Using `always` more than twice in the same menu is a bad idea.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, Scope.ALL_RESOURCE_FILES)
    );

    private int alwaysCount = 0;
    private boolean ifRoomFound = false;

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("SHOW_AS_ACTION_ALWAYS");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getName().equals("SHOW_AS_ACTION_ALWAYS")) {
            alwaysCount++;
            if (!ifRoomFound && alwaysCount > 2) {
                context.report(ISSUE, node, context.getLocation(node), "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` more than twice is a bad idea.");
            }
        } else if (method.getName().equals("SHOW_AS_ACTION_IF_ROOM")) {
            ifRoomFound = true;
        }
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if ("always".equals(value)) {
            alwaysCount++;
            if (!ifRoomFound && alwaysCount > 2) {
                context.report(ISSUE, attribute, context.getLocation(attribute), "Using `showAsAction=always` more than twice is a bad idea.");
            }
        } else if ("ifRoom".equals(value)) {
            ifRoomFound = true;
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        alwaysCount = 0;
        ifRoomFound = false;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceType.MENU.equals(folderType.getResourceType());
    }
}