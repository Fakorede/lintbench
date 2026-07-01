package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String AUTO_URI = "http://schemas.android.com/apk/res-auto";
    private static final String TAG_MERGE = "merge";

    private static final Implementation IMPLEMENTATION = new Implementation(
            ObsoleteLayoutParamsDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION)
            .setAndroidSpecific(true);

    private static final Map<String, Set<String>> ALLOWED = new HashMap<>();

    static {
        Set<String> base = new HashSet<>();
        base.add("layout_width");
        base.add("layout_height");
        base.add("layout_margin");
        base.add("layout_marginBottom");
        base.add("layout_marginEnd");
        base.add("layout_marginLeft");
        base.add("layout_marginRight");
        base.add("layout_marginStart");
        base.add("layout_marginTop");
        base.add("layout_marginHorizontal");
        base.add("layout_marginVertical");

        Set<String> linear = new HashSet<>(base);
        linear.add("layout_weight");
        linear.add("layout_gravity");
        ALLOWED.put("LinearLayout", linear);

        Set<String> frame = new HashSet<>(base);
        frame.add("layout_gravity");
        ALLOWED.put("FrameLayout", frame);

        Set<String> relative = new HashSet<>(base);
        relative.add("layout_above");
        relative.add("layout_alignBaseline");
        relative.add("layout_alignBottom");
        relative.add("layout_alignEnd");
        relative.add("layout_alignLeft");
        relative.add("layout_alignParentBottom");
        relative.add("layout_alignParentEnd");
        relative.add("layout_alignParentLeft");
        relative.add("layout_alignParentRight");
        relative.add("layout_alignParentStart");
        relative.add("layout_alignParentTop");
        relative.add("layout_alignRight");
        relative.add("layout_alignStart");
        relative.add("layout_alignTop");
        relative.add("layout_alignWithParentIfMissing");
        relative.add("layout_below");
        relative.add("layout_centerHorizontal");
        relative.add("layout_centerInParent");
        relative.add("layout_centerVertical");
        relative.add("layout_toEndOf");
        relative.add("layout_toLeftOf");
        relative.add("layout_toRightOf");
        relative.add("layout_toStartOf");
        ALLOWED.put("RelativeLayout", relative);

        Set<String> grid = new HashSet<>(base);
        grid.add("layout_row");
        grid.add("layout_column");
        grid.add("layout_rowSpan");
        grid.add("layout_columnSpan");
        grid.add("layout_gravity");
        ALLOWED.put("GridLayout", grid);

        Set<String> tableRow = new HashSet<>(base);
        tableRow.add("layout_column");
        tableRow.add("layout_span");
        ALLOWED.put("TableRow", tableRow);

        Set<String> drawer = new HashSet<>(base);
        drawer.add("layout_gravity");
        ALLOWED.put("DrawerLayout", drawer);

        Set<String> sliding = new HashSet<>(base);
        sliding.add("layout_weight");
        ALLOWED.put("SlidingPaneLayout", sliding);

        Set<String> coordinator = new HashSet<>(base);
        coordinator.add("layout_behavior");
        coordinator.add("layout_anchor");
        coordinator.add("layout_anchorGravity");
        coordinator.add("layout_dodgeInsetEdges");
        coordinator.add("layout_insetEdge");
        coordinator.add("layout_keyline");
        ALLOWED.put("CoordinatorLayout", coordinator);

        Set<String> constraint = new HashSet<>(base);
        constraint.add("layout_constraintLeft_toLeftOf");
        constraint.add("layout_constraintLeft_toRightOf");
        constraint.add("layout_constraintRight_toLeftOf");
        constraint.add("layout_constraintRight_toRightOf");
        constraint.add("layout_constraintTop_toTopOf");
        constraint.add("layout_constraintTop_toBottomOf");
        constraint.add("layout_constraintBottom_toTopOf");
        constraint.add("layout_constraintBottom_toBottomOf");
        constraint.add("layout_constraintBaseline_toBaselineOf");
        constraint.add("layout_constraintStart_toEndOf");
        constraint.add("layout_constraintStart_toStartOf");
        constraint.add("layout_constraintEnd_toStartOf");
        constraint.add("layout_constraintEnd_toEndOf");
        constraint.add("layout_constraintHorizontal_bias");
        constraint.add("layout_constraintVertical_bias");
        constraint.add("layout_constraintDimensionRatio");
        constraint.add("layout_constraintHeight_default");
        constraint.add("layout_constraintHeight_min");
        constraint.add("layout_constraintHeight_max");
        constraint.add("layout_constraintHeight_percent");
        constraint.add("layout_constraintWidth_default");
        constraint.add("layout_constraintWidth_min");
        constraint.add("layout_constraintWidth_max");
        constraint.add("layout_constraintWidth_percent");
        constraint.add("layout_constraintHorizontal_chainStyle");
        constraint.add("layout_constraintVertical_chainStyle");
        constraint.add("layout_constraintHorizontal_weight");
        constraint.add("layout_constraintVertical_weight");
        constraint.add("layout_constraintCircle");
        constraint.add("layout_constraintCircleAngle");
        constraint.add("layout_constraintCircleRadius");
        constraint.add("layout_editor_absoluteX");
        constraint.add("layout_editor_absoluteY");
        constraint.add("layout_goneMarginLeft");
        constraint.add("layout_goneMarginTop");
        constraint.add("layout_goneMarginRight");
        constraint.add("layout_goneMarginBottom");
        constraint.add("layout_goneMarginStart");
        constraint.add("layout_goneMarginEnd");
        constraint.add("layout_constrainedWidth");
        constraint.add("layout_constrainedHeight");
        ALLOWED.put("ConstraintLayout", constraint);

        Set<String> appBar = new HashSet<>(base);
        appBar.add("layout_scrollFlags");
        ALLOWED.put("AppBarLayout", appBar);

        Set<String> collapsing = new HashSet<>(base);
        collapsing.add("layout_collapseMode");
        collapsing.add("layout_collapseParallaxMultiplier");
        ALLOWED.put("CollapsingToolbarLayout", collapsing);

        Set<String> flexbox = new HashSet<>(base);
        flexbox.add("layout_order");
        flexbox.add("layout_flexGrow");
        flexbox.add("layout_flexShrink");
        flexbox.add("layout_alignSelf");
        flexbox.add("layout_minWidth");
        flexbox.add("layout_minHeight");
        flexbox.add("layout_maxWidth");
        flexbox.add("layout_maxHeight");
        flexbox.add("layout_wrapBefore");
        ALLOWED.put("FlexboxLayout", flexbox);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentName = parent.getTagName();
        if (TAG_MERGE.equals(parentName)) {
            return;
        }

        String simpleName = getSimpleName(parentName);
        Set<String> allowed = ALLOWED.get(simpleName);
        if (allowed == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String namespace = attr.getNamespaceURI();
            if (namespace == null
                    || (!namespace.equals(ANDROID_URI) && !namespace.equals(AUTO_URI))) {
                continue;
            }

            String localName = attr.getLocalName();
            if (localName == null) {
                String name = attr.getName();
                int colon = name.indexOf(':');
                localName = colon != -1 ? name.substring(colon + 1) : name;
            }

            if (!localName.startsWith("layout_")) {
                continue;
            }

            if (!allowed.contains(localName)) {
                String message = String.format(
                        "Invalid layout param in a `%1$s`: `%2$s`",
                        parentName, attr.getName());
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private static String getSimpleName(String tagName) {
        int index = tagName.lastIndexOf('.');
        return index != -1 && index < tagName.length() - 1
                ? tagName.substring(index + 1)
                : tagName;
    }
}