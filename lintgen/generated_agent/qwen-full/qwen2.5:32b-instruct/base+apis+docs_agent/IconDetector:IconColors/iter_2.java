package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;
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
                    EnumSet.of(Scope.RESOURCE_FILE)
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");

        if (isActionBarIcon(name) || isNotificationIcon(name)) {
            checkColors(context, element);
        }
    }

    private boolean isActionBarIcon(String name) {
        return name.startsWith("ic_menu_");
    }

    private boolean isNotificationIcon(String name) {
        return name.startsWith("ic_stat_");
    }

    private void checkColors(XmlContext context, Element element) {
        // Placeholder for logic to check if the icon colors are white or shades of gray
        // This would involve parsing the XML content and checking color values.
        // For simplicity, this detector will assume that all icons need a warning.

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Icon colors should only be white and shades of gray."
        );
    }
}