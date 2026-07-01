package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move "
                            + "view code around without updating the layout params. This will cause "
                            + "useless attribute processing at runtime, and is misleading for others "
                            + "reading the layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    // Layout params that are specific to certain parent layouts
    // Maps layout param attribute name -> set of parent layouts that support it

    // Params defined by LinearLayout
    private static final Set<String> LINEAR_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_weight",
            "layout_gravity"
    ));

    // Params defined by RelativeLayout
    private static final Set<String> RELATIVE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_above",
            "layout_below",
            "layout_toLeftOf",
            "layout_toRightOf",
            "layout_toStartOf",
            "layout_toEndOf",
            "layout_alignTop",
            "layout_alignBottom",
            "layout_alignLeft",
            "layout_alignRight",
            "layout_alignStart",
            "layout_alignEnd",
            "layout_alignBaseline",
            "layout_alignParentTop",
            "layout_alignParentBottom",
            "layout_alignParentLeft",
            "layout_alignParentRight",
            "layout_alignParentStart",
            "layout_alignParentEnd",
            "layout_centerHorizontal",
            "layout_centerVertical",
            "layout_centerInParent"
    ));

    // Params defined by GridLayout
    private static final Set<String> GRID_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_row",
            "layout_rowSpan",
            "layout_rowWeight",
            "layout_column",
            "layout_columnSpan",
            "layout_columnWeight",
            "layout_gravity"
    ));

    // Params defined by TableLayout / TableRow
    private static final Set<String> TABLE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_column",
            "layout_span"
    ));

    // Params defined by AbsoluteLayout (deprecated)
    private static final Set<String> ABSOLUTE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_x",
            "layout_y"
    ));

    // Params defined by ConstraintLayout
    private static final Set<String> CONSTRAINT_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintHorizontal_bias",
            "layout_constraintVertical_bias",
            "layout_constraintHorizontal_chainStyle",
            "layout_constraintVertical_chainStyle",
            "layout_constraintHorizontal_weight",
            "layout_constraintVertical_weight",
            "layout_constraintWidth_default",
            "layout_constraintHeight_default",
            "layout_constraintWidth_min",
            "layout_constraintWidth_max",
            "layout_constraintWidth_percent",
            "layout_constraintHeight_min",
            "layout_constraintHeight_max",
            "layout_constraintHeight_percent",
            "layout_constraintDimensionRatio",
            "layout_constraintGuide_begin",
            "layout_constraintGuide_end",
            "layout_constraintGuide_percent",
            "layout_constraintCircle",
            "layout_constraintCircleRadius",
            "layout_constraintCircleAngle",
            "layout_editor_absoluteX",
            "layout_editor_absoluteY",
            "layout_goneMarginBottom",
            "layout_goneMarginEnd",
            "layout_goneMarginLeft",
            "layout_goneMarginRight",
            "layout_goneMarginStart",
            "layout_goneMarginTop"
    ));

    // Map from layout param local name -> set of parent layout tags that support it
    private static final Map<String, Set<String>> PARAM_TO_LAYOUTS = new HashMap<>();

    static {
        // LinearLayout params
        for (String param : LINEAR_LAYOUT_PARAMS) {
            addMapping(param, "LinearLayout", "RadioGroup", "TableLayout", "TableRow",
                    "SearchView", "ZoomControls");
        }

        // RelativeLayout params
        for (String param : RELATIVE_LAYOUT_PARAMS) {
            addMapping(param, "RelativeLayout");
        }

        // GridLayout params
        for (String param : GRID_LAYOUT_PARAMS) {
            addMapping(param, "GridLayout");
        }

        // TableLayout/TableRow params
        for (String param : TABLE_LAYOUT_PARAMS) {
            addMapping(param, "TableRow");
        }

        // AbsoluteLayout params
        for (String param : ABSOLUTE_LAYOUT_PARAMS) {
            addMapping(param, "AbsoluteLayout");
        }

        // ConstraintLayout params
        for (String param : CONSTRAINT_LAYOUT_PARAMS) {
            addMapping(param, "ConstraintLayout", "android.support.constraint.ConstraintLayout",
                    "androidx.constraintlayout.widget.ConstraintLayout");
        }
    }

    private static void addMapping(String param, String... layouts) {
        Set<String> set = PARAM_TO_LAYOUTS.get(param);
        if (set == null) {
            set = new HashSet<>();
            PARAM_TO_LAYOUTS.put(param, set);
        }
        for (String layout : layouts) {
            set.add(layout);
        }
    }

    // Collect pending reports so we can verify include scenarios
    // Maps element -> list of (attribute, location, message) tuples
    private final Map<Element, List<PendingReport>> mPendingReports = new HashMap<>();

    // Track elements that are root elements (possibly included into other layouts)
    private final Set<Element> mRootElements = new HashSet<>();

    // Track include elements so we know which layouts are included
    private final List<XmlContext> mContexts = new ArrayList<>();

    private static class PendingReport {
        final XmlContext context;
        final Attr attribute;
        final Location location;
        final String message;

        PendingReport(XmlContext context, Attr attribute, Location location, String message) {
            this.context = context;
            this.attribute = attribute;
            this.location = location;
            this.message = message;
        }
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return new ArrayList<>(PARAM_TO_LAYOUTS.keySet());
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() == Node.DOCUMENT_NODE) {
            mRootElements.add(element);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
            // Strip android: prefix if present
            if (localName.startsWith("android:")) {
                localName = localName.substring("android:".length());
            }
        }

        if (!localName.startsWith("layout_")) {
            return;
        }

        Set<String> validParents = PARAM_TO_LAYOUTS.get(localName);
        if (validParents == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();

        if (parentNode == null || parentNode.getNodeType() == Node.DOCUMENT_NODE) {
            // Root element - might be included into a layout, defer reporting
            addPendingReport(element, context, attribute,
                    context.getLocation(attribute),
                    buildMessage(localName, null));
            return;
        }

        if (parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parentElement = (Element) parentNode;
        String parentTag = parentElement.getTagName();

        // Strip package prefix for comparison
        String parentSimpleName = getSimpleName(parentTag);

        if (!isValidParent(parentTag, parentSimpleName, validParents)) {
            String message = buildMessage(localName, parentTag);
            LintFix fix = LintFix.create()
                    .name("Remove attribute")
                    .unset(attribute.getNamespaceURI(), attribute.getName())
                    .build();
            context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
        }
    }

    private boolean isValidParent(String parentTag, String parentSimpleName,
            Set<String> validParents) {
        if (validParents.contains(parentTag)) {
            return true;
        }
        if (validParents.contains(parentSimpleName)) {
            return true;
        }
        // Check suffix match for fully qualified names
        for (String valid : validParents) {
            if (parentTag.endsWith("." + valid) || parentSimpleName.equals(valid)) {
                return true;
            }
        }
        return false;
    }

    private String getSimpleName(String tag) {
        int dotIndex = tag.lastIndexOf('.');
        if (dotIndex >= 0) {
            return tag.substring(dotIndex + 1);
        }
        return tag;
    }

    private String buildMessage(String paramName, @Nullable String parentTag) {
        if (parentTag != null) {
            return "Invalid layout param in a `" + parentTag + "` (param `" + paramName
                    + "` is not defined for `" + parentTag + "`)";
        }
        return "Invalid layout param `" + paramName + "` for this layout";
    }

    private void addPendingReport(Element element, XmlContext context, Attr attribute,
            Location location, String message) {
        List<PendingReport> reports = mPendingReports.get(element);
        if (reports == null) {
            reports = new ArrayList<>();
            mPendingReports.put(element, reports);
        }
        reports.add(new PendingReport(context, attribute, location, message));
    }

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        // For any pending reports on root elements, we report them since we can't
        // easily verify what parent they'll be included into at this point.
        // In a more complete implementation, we'd track <include> tags and cross-reference.
        for (Map.Entry<Element, List<PendingReport>> entry : mPendingReports.entrySet()) {
            for (PendingReport report : entry.getValue()) {
                String localName = report.attribute.getLocalName();
                if (localName == null) {
                    localName = report.attribute.getName();
                    if (localName.startsWith("android:")) {
                        localName = localName.substring("android:".length());
                    }
                }
                // Only report if the root element itself doesn't match any valid parent
                // (i.e., it's truly an orphan layout param on a root view)
                report.context.report(ISSUE, report.attribute, report.location, report.message);
            }
        }
        mPendingReports.clear();
        mRootElements.clear();
    }
}