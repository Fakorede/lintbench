package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no effect. " +
                    "This usually happens when you change the parent layout or move view code around without " +
                    "updating the layout params. This will cause useless attribute processing at runtime, " +
                    "and is misleading for others reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Detector.ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null || !name.startsWith("layout_")) {
            return;
        }

        String param = name.substring(7); // strip "layout_" prefix
        // Width, height and margins are supported by almost all layouts via ViewGroup.LayoutParams and MarginLayoutParams
        if (param.equals("width") || param.equals("height") || param.startsWith("margin")) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) return;

        Node parentNode = owner.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) return;

        String parentTag = ((Element) parentNode).getTagName();
        // Skip merge and include since the actual parent is determined at runtime
        if (parentTag.equals("merge") || parentTag.equals("include")) {
            return;
        }

        if (isObsolete(parentTag, param)) {
            String message = String.format("Invalid layout param in a `%s`: `%s`", parentTag, name);
            context.report(ISSUE, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op: attribute checking is handled in visitAttribute
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    private static boolean isObsolete(String parentTag, String param) {
        Set<String> allowed = getAllowedParents(param);
        if (allowed == null) {
            return false; // Unknown param, assume valid to avoid false positives
        }
        for (String allowedParent : allowed) {
            if (parentTag.endsWith(allowedParent)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> getAllowedParents(String param) {
        switch (param) {
            case "weight":
                return set("LinearLayout", "RadioGroup");
            case "gravity":
                return set("FrameLayout", "LinearLayout", "DrawerLayout", "CoordinatorLayout",
                           "GridLayout", "TableRow", "Toolbar", "RadioGroup");
            case "x":
            case "y":
                return set("AbsoluteLayout");
            case "span":
                return set("TableRow");
        }

        if (param.startsWith("align") || param.equals("below") || param.equals("above") ||
            param.startsWith("toLeftOf") || param.startsWith("toRightOf") ||
            param.startsWith("toStartOf") || param.startsWith("toEndOf") ||
            param.startsWith("center")) {
            return set("RelativeLayout");
        }

        if (param.startsWith("constraint") || param.startsWith("goneMargin") ||
            param.startsWith("editor_absolute")) {
            return set("ConstraintLayout", "MotionLayout");
        }

        if (param.equals("row") || param.equals("column") || param.equals("rowSpan") ||
            param.equals("columnSpan") || param.equals("rowWeight") || param.equals("columnWeight")) {
            return set("GridLayout");
        }

        if (param.startsWith("anchor") || param.equals("behavior") ||
            param.startsWith("dodgeInset") || param.startsWith("insetEdge") || param.equals("keyline")) {
            return set("CoordinatorLayout");
        }

        if (param.endsWith("Percent") || param.equals("aspectRatio")) {
            return set("PercentFrameLayout", "PercentRelativeLayout");
        }

        return null;
    }

    private static Set<String> set(String... parents) {
        return new HashSet<>(Arrays.asList(parents));
    }
}