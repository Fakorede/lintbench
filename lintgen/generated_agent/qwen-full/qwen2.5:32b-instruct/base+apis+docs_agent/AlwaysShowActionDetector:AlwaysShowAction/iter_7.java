package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UCallExpression;
import org.w3c.dom.Attr;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Using `showAsAction=always` in menu XML or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide.",
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead. Using `always` more than twice in the same menu is a bad idea.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.ALL_RESOURCE_FILES))
    );

    private int alwaysCount = 0;
    private boolean ifRoomFound = false;

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("SHOW_AS_ACTION_ALWAYS");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node) {
        String methodName = node.getMethodName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(methodName)) {
            alwaysCount++;
            if (!ifRoomFound && alwaysCount > 2) {
                context.report(ISSUE, node, context.getLocation(node), "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` more than twice is a bad idea.");
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(methodName)) {
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
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        alwaysCount = 0;
        ifRoomFound = false;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceType.MENU == folderType.getResourceType();
    }
}