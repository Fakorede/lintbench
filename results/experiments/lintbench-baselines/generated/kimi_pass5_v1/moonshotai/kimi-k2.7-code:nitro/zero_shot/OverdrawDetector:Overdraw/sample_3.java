package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends Detector implements Detector.XmlScanner, SourceCodeScanner {
    private static final Pattern SET_CONTENT_VIEW_PATTERN =
            Pattern.compile("R\\s*\\.\\s*layout\\s*\\.\\s*([\\w\\.]+)");

    private final Map<String, List<String>> mLayoutToActivities = new HashMap<>();

    public static final Issue OVERDRAW = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a "
                    + "custom theme where the theme background is null. Otherwise, the theme "
                    + "background will be painted first, only to have your custom background "
                    + "completely cover it; this is called \"overdraw\".\n\n"
                    + "This detector relies on figuring out which layouts are associated with "
                    + "which activities based on scanning the Java code, and uses an inexact "
                    + "pattern matching algorithm. It can therefore incorrectly conclude which "
                    + "activity the layout is associated with and wrongly complain that a "
                    + "background theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then consider making "
                    + "a custom theme with your custom background and just using that theme "
                    + "instead of a root element background.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutToActivities.clear();
    }

    @Override
    @NonNull
    public List<String> getApplicableCallNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        UClass containingClass = UastUtils.getParentOfType(call, UClass.class, false);
        if (containingClass == null) {
            return;
        }

        String activity = containingClass.getQualifiedName();
        if (activity == null) {
            return;
        }

        for (UExpression arg : call.getValueArguments()) {
            String text = arg != null ? arg.asSourceString() : null;
            if (text == null) {
                continue;
            }

            Matcher matcher = SET_CONTENT_VIEW_PATTERN.matcher(text);
            while (matcher.find()) {
                String layout = matcher.group(1);
                List<String> activities = mLayoutToActivities.get(layout);
                if (activities == null) {
                    activities = new ArrayList<>();
                    mLayoutToActivities.put(layout, activities);
                }
                if (!activities.contains(activity)) {
                    activities.add(activity);
                }
            }
        }
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attr) {
        if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
            return;
        }

        Element owner = attr.getOwnerElement();
        Node parent = owner.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String layoutName = getBaseLayoutName(context.file.getName());
        List<String> activities = mLayoutToActivities.get(layoutName);
        if (activities == null || activities.isEmpty()) {
            return;
        }

        String message = String.format(
                "Possible overdraw: this root view paints a background with %1$s=\"%2$s\". "
                        + "The activity's theme likely also paints a background, so the screen "
                        + "contents are being drawn twice. Consider setting the theme's "
                        + "windowBackground to null and using this drawable as the theme "
                        + "background instead. (This layout is associated with: %3$s)",
                ATTR_BACKGROUND,
                attr.getValue(),
                join(activities));

        context.report(OVERDRAW, attr, context.getLocation(attr), message);
    }

    private static String getBaseLayoutName(String fileName) {
        if (fileName.endsWith(".xml")) {
            fileName = fileName.substring(0, fileName.length() - ".xml".length());
        }
        int dash = fileName.indexOf('-');
        if (dash != -1) {
            fileName = fileName.substring(0, dash);
        }
        return fileName;
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }
}