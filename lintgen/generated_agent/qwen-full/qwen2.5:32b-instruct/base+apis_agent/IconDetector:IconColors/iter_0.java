package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconColors",
            "Notification icons and Action Bar icons should only be white and shades of gray.",
            "See the Android Design Guide for more details. Note that Lint decides whether an icon is an action bar icon or a notification icon based on the filename prefix: `ic_menu_` for action bar icons, `ic_stat_` for notification icons etc.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = getIconName(element);
        if (name != null && !isValidColor(name)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The icon color does not follow the recommended visual style.");
        }
    }

    private String getIconName(Element element) {
        Attr nameAttr = element.getAttributeNode("android:name");
        if (nameAttr != null) {
            return nameAttr.getValue();
        }
        return null;
    }

    private boolean isValidColor(String iconName) {
        // Check for action bar icons and notification icons
        if (iconName.startsWith("ic_menu_") || iconName.startsWith("ic_stat_")) {
            // Validate that the icon is white or shades of gray
            // This is a placeholder logic, actual implementation would require image processing
            return true;  // Placeholder validation
        }
        return false;
    }

}