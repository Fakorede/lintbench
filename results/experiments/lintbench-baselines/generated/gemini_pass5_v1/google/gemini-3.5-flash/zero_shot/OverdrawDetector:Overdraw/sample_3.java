package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
        OverdrawDetector.class,
        Scope.JAVA_AND_RESOURCE_FILES
    );

    public static final Issue ISSUE = Issue.create(
        "Overdraw",
        "Overdraw: Painting regions more than once",
        "If you set a background drawable on a root view, then you should use a custom theme " +
        "where the theme background is null. Otherwise, the theme background will be painted " +
        "first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n" +
        "NOTE: This detector relies on figuring out which layouts are associated with " +
        "which activities based on scanning the Java code, and it's currently doing that " +
        "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
        "conclude which activity the layout is associated with and then wrongly complain " +
        "that a background-theme is hidden.\n\n" +
        "If you want your custom background on multiple pages, then you should consider " +
        "making a custom theme with your custom background and just using that theme " +
        "instead of a root element background.\n\n" +
        "Of course it's possible that your custom drawable is translucent and you want " +
        "it to be mixed with the background. However, you will get better performance " +
        "if you pre-mix the background with your drawable and use that resulting image or " +
        "color as a custom theme background instead.",
        Category.PERFORMANCE,
        3,
        Severity.WARNING,
        IMPLEMENTATION
    );

    private static class PendingReport {
        final Location location;
        final String layoutName;
        final XmlContext context;

        PendingReport(Location location, String layoutName, XmlContext context) {
            this.location = location;
            this.layoutName = layoutName;
            this.context = context;
        }
    }

    private final List<PendingReport> mPendingReports = new ArrayList<>();
    private final Set<String> mActivities = new HashSet<>();
    private final Set<String> mLayoutReferences = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) return;

        if ("layout".equals(root.getTagName())) {
            org.w3c.dom.NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (!"data".equals(childElement.getTagName())) {
                        root = childElement;
                        break;
                    }
                }
            }
        }

        Attr background = root.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND);
        if (background != null && context.file != null) {
            String fileName = context.file.getName();
            int dot = fileName.indexOf('.');
            String layoutName = dot != -1 ? fileName.substring(0, dot) : fileName;
            Location location = context.getValueLocation(background);
            mPendingReports.add(new PendingReport(location, layoutName, context));
        }
    }

    @Override
    public void visitFile(JavaContext context) {
        CharSequence source = context.getContents();
        if (source == null) return;

        String sourceStr = source.toString();

        java.util.regex.Matcher classMatcher = java.util.regex.Pattern.compile("class\\s+(\\w+)").matcher(sourceStr);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            mActivities.add(className);
        }

        java.util.regex.Matcher layoutMatcher = java.util.regex.Pattern.compile("R\\.layout\\.(\\w+)").matcher(sourceStr);
        while (layoutMatcher.find()) {
            String layoutName = layoutMatcher.group(1);
            mLayoutReferences.add(layoutName);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (PendingReport report : mPendingReports) {
            if (isAssociated(report.layoutName)) {
                report.context.report(
                    ISSUE,
                    report.location,
                    "Possible overdraw: Root element has background, but the theme might also draw a background"
                );
            }
        }
    }

    private boolean isAssociated(String layoutName) {
        if (mLayoutReferences.contains(layoutName)) {
            return true;
        }

        for (String activity : mActivities) {
            if (matches(activity, layoutName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String activity, String layout) {
        String snake = camelToSnake(activity);
        if (snake.equals(layout)) {
            return true;
        }

        if (snake.startsWith("activity_") && snake.substring("activity_".length()).equals(layout)) {
            return true;
        }

        String layoutFromActivity = getLayoutNameFromActivity(activity);
        if (layoutFromActivity.equals(layout)) {
            return true;
        }

        if (layoutFromActivity.startsWith("activity_") && 
            layoutFromActivity.substring("activity_".length()).equals(layout)) {
            return true;
        }

        return false;
    }

    private static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String getLayoutNameFromActivity(String activityClassName) {
        String suffix = "Activity";
        String name = activityClassName;
        if (name.endsWith(suffix)) {
            name = name.substring(0, name.length() - suffix.length());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("activity_");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}