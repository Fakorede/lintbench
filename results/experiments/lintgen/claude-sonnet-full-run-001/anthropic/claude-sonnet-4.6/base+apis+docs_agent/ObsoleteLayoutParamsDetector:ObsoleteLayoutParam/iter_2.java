package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Checks whether layout params are valid for the given parent layout.
 */
public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    // Layout params valid for ALL layouts (ViewGroup.LayoutParams)
    private static final Set<String> COMMON_PARAMS = new HashSet<>();
    static {
        COMMON_PARAMS.add("layout_width");
        COMMON_PARAMS.add("layout_height");
    }

    // Layout params valid for layouts supporting margins (ViewGroup.MarginLayoutParams)
    private static final Set<String> MARGIN_PARAMS = new HashSet<>();
    static {
        MARGIN_PARAMS.add("layout_margin");
        MARGIN_PARAMS.add("layout_marginLeft");
        MARGIN_PARAMS.add("layout_marginRight");
        MARGIN_PARAMS.add("layout_marginTop");
        MARGIN_PARAMS.add("layout_marginBottom");
        MARGIN_PARAMS.add("layout_marginStart");
        MARGIN_PARAMS.add("layout_marginEnd");
        MARGIN_PARAMS.add("layout_marginHorizontal");
        MARGIN_PARAMS.add("layout_marginVertical");
    }

    // All valid params per layout type (complete sets including common + margin + specific)
    private static final Map<String, Set<String>> VALID_PARAMS_MAP = new HashMap<>();

    static {
        // LinearLayout: common + margin + gravity + weight
        Set<String> linearParams = new HashSet<>();
        linearParams.addAll(COMMON_PARAMS);
        linearParams.addAll(MARGIN_PARAMS);
        linearParams.add("layout_gravity");
        linearParams.add("layout_weight");
        VALID_PARAMS_MAP.put("LinearLayout", linearParams);
        VALID_PARAMS_MAP.put("android.widget.LinearLayout", linearParams);

        // RelativeLayout: common + margin + relative params
        Set<String> relativeParams = new HashSet<>();
        relativeParams.addAll(COMMON_PARAMS);
        relativeParams.addAll(MARGIN_PARAMS);
        relativeParams.add("layout_above");
        relativeParams.add("layout_below");
        relativeParams.add("layout_toLeftOf");
        relativeParams.add("layout_toRightOf");
        relativeParams.add("layout_toStartOf");
        relativeParams.add("layout_toEndOf");
        relativeParams.add("layout_alignTop");
        relativeParams.add("layout_alignBottom");
        relativeParams.add("layout_alignLeft");
        relativeParams.add("layout_alignRight");
        relativeParams.add("layout_alignStart");
        relativeParams.add("layout_alignEnd");
        relativeParams.add("layout_alignBaseline");
        relativeParams.add("layout_alignParentTop");
        relativeParams.add("layout_alignParentBottom");
        relativeParams.add("layout_alignParentLeft");
        relativeParams.add("layout_alignParentRight");
        relativeParams.add("layout_alignParentStart");
        relativeParams.add("layout_alignParentEnd");
        relativeParams.add("layout_centerInParent");
        relativeParams.add("layout_centerHorizontal");
        relativeParams.add("layout_centerVertical");
        relativeParams.add("layout_alignWithParentIfMissing");
        VALID_PARAMS_MAP.put("RelativeLayout", relativeParams);
        VALID_PARAMS_MAP.put("android.widget.RelativeLayout", relativeParams);

        // FrameLayout: common + margin + gravity
        Set<String> frameParams = new HashSet<>();
        frameParams.addAll(COMMON_PARAMS);
        frameParams.addAll(MARGIN_PARAMS);
        frameParams.add("layout_gravity");
        VALID_PARAMS_MAP.put("FrameLayout", frameParams);
        VALID_PARAMS_MAP.put("android.widget.FrameLayout", frameParams);

        // TableLayout: common + margin + gravity + span + column
        Set<String> tableLayoutParams = new HashSet<>();
        tableLayoutParams.addAll(COMMON_PARAMS);
        tableLayoutParams.addAll(MARGIN_PARAMS);
        tableLayoutParams.add("layout_gravity");
        tableLayoutParams.add("layout_column");
        tableLayoutParams.add("layout_span");
        VALID_PARAMS_MAP.put("TableLayout", tableLayoutParams);
        VALID_PARAMS_MAP.put("android.widget.TableLayout", tableLayoutParams);

        // TableRow: common + margin + gravity + span + column
        Set<String> tableRowParams = new HashSet<>();
        tableRowParams.addAll(COMMON_PARAMS);
        tableRowParams.addAll(MARGIN_PARAMS);
        tableRowParams.add("layout_gravity");
        tableRowParams.add("layout_column");
        tableRowParams.add("layout_span");
        VALID_PARAMS_MAP.put("TableRow", tableRowParams);
        VALID_PARAMS_MAP.put("android.widget.TableRow", tableRowParams);

        // GridLayout: common + gravity + row + column + rowSpan + columnSpan + rowWeight + columnWeight
        Set<String> gridLayoutParams = new HashSet<>();
        gridLayoutParams.addAll(COMMON_PARAMS);
        gridLayoutParams.add("layout_gravity");
        gridLayoutParams.add("layout_row");
        gridLayoutParams.add("layout_rowSpan");
        gridLayoutParams.add("layout_rowWeight");
        gridLayoutParams.add("layout_column");
        gridLayoutParams.add("layout_columnSpan");
        gridLayoutParams.add("layout_columnWeight");
        VALID_PARAMS_MAP.put("GridLayout", gridLayoutParams);
        VALID_PARAMS_MAP.put("android.widget.GridLayout", gridLayoutParams);
        VALID_PARAMS_MAP.put("androidx.gridlayout.widget.GridLayout", gridLayoutParams);

        // AbsoluteLayout: common + x + y (no margins)
        Set<String> absoluteParams = new HashSet<>();
        absoluteParams.addAll(COMMON_PARAMS);
        absoluteParams.add("layout_x");
        absoluteParams.add("layout_y");
        VALID_PARAMS_MAP.put("AbsoluteLayout", absoluteParams);
        VALID_PARAMS_MAP.put("android.widget.AbsoluteLayout", absoluteParams);

        // ScrollView extends FrameLayout
        VALID_PARAMS_MAP.put("ScrollView", frameParams);
        VALID_PARAMS_MAP.put("android.widget.ScrollView", frameParams);
        VALID_PARAMS_MAP.put("HorizontalScrollView", frameParams);
        VALID_PARAMS_MAP.put("android.widget.HorizontalScrollView", frameParams);

        // GridView: children use AbsListView.LayoutParams (only width/height)
        Set<String> gridViewParams = new HashSet<>();
        gridViewParams.addAll(COMMON_PARAMS);
        VALID_PARAMS_MAP.put("GridView", gridViewParams);
        VALID_PARAMS_MAP.put("android.widget.GridView", gridViewParams);

        // ListView: children use AbsListView.LayoutParams (only width/height)
        Set<String> listViewParams = new HashSet<>();
        listViewParams.addAll(COMMON_PARAMS);
        VALID_PARAMS_MAP.put("ListView", listViewParams);
        VALID_PARAMS_MAP.put("android.widget.ListView", listViewParams);
    }

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the parent element to determine what layout params are valid
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            // Root element or no parent - no layout params to validate
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        // Get the valid params for this parent layout
        Set<String> validParams = VALID_PARAMS_MAP.get(parentTag);

        // If we don't know the parent layout, we can't validate
        if (validParams == null) {
            return;
        }

        // Check all attributes of the element
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            String namespace = attr.getNamespaceURI();

            // Only check android namespace attributes that start with "layout_"
            if (!ANDROID_URI.equals(namespace) || name == null || !name.startsWith("layout_")) {
                continue;
            }

            // Check if this layout param is valid for the parent
            if (!validParams.contains(name)) {
                context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        String.format(
                                "Invalid layout param in a `%1$s`: `%2$s`",
                                parentTag,
                                name));
            }
        }
    }
}