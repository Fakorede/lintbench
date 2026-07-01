package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.*;

public class ObsoleteLayoutParamsDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no " +
            "effect. This usually happens when you change the parent layout or move view " +
            "code around without updating the layout params. This will cause useless " +
            "attribute processing at runtime, and is misleading for others reading the " +
            "layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Set<String> BASE_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_width", "layout_height"
    ));

    private static final Set<String> MARGIN_LAYOUT_PARAMS = new HashSet<>(Arrays.asList(
            "layout_margin", "layout_marginLeft", "layout_marginTop", "layout_marginRight",
            "layout_marginBottom", "layout_marginStart", "layout_marginEnd",
            "layout_marginHorizontal", "layout_marginVertical"
    ));

    private static final Map<String, Set<String>> LAYOUT_PARAMS_MAP = new HashMap<>();

    static {
        LAYOUT_PARAMS_MAP.put("LinearLayout", new HashSet<>(Arrays.asList("layout_weight", "layout_gravity")));
        LAYOUT_PARAMS_MAP.put("RadioGroup", new HashSet<>(Arrays.asList("layout_weight", "layout_gravity")));
        LAYOUT_PARAMS_MAP.put("FrameLayout", new HashSet<>(Collections.singletonList("layout_gravity")));
        LAYOUT_PARAMS_MAP.put("RelativeLayout", new HashSet<>(Arrays.asList(
                "layout_above", "layout_alignBaseline", "layout_alignBottom", "layout_alignEnd",
                "layout_alignLeft", "layout_alignParentBottom", "layout_alignParentEnd",
                "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentStart",
                "layout_alignParentTop", "layout_alignRight", "layout_alignStart", "layout_alignTop",
                "layout_alignWithParentIfMissing", "layout_below", "layout_centerHorizontal",
                "layout_centerInParent", "layout_centerVertical", "layout_toEndOf", "layout_toLeftOf",
                "layout_toRightOf", "layout_toStartOf"
        )));
        LAYOUT_PARAMS_MAP.put("ConstraintLayout", new HashSet<>(Arrays.asList(
                "layout_constraintTop_toTopOf", "layout_constraintTop_toBottomOf",
                "layout_constraintBottom_toTopOf", "layout_constraintBottom_toBottomOf",
                "layout_constraintLeft_toLeftOf", "layout_constraintLeft_toRightOf",
                "layout_constraintRight_toLeftOf", "layout_constraintRight_toRightOf",
                "layout_constraintStart_toStartOf", "layout_constraintStart_toEndOf",
                "layout_constraintEnd_toStartOf", "layout_constraintEnd_toEndOf",
                "layout_constraintBaseline_toBaselineOf", "layout_constraintCircle",
                "layout_constraintCircleRadius", "layout_constraintCircleAngle",
                "layout_constraintWidth_default", "layout_constraintHeight_default",
                "layout_constraintWidth_min", "layout_constraintWidth_max",
                "layout_constraintHeight_min", "layout_constraintHeight_max",
                "layout_constraintWidth_percent", "layout_constraintHeight_percent",
                "layout_constraintDimensionRatio", "layout_constraintHorizontal_bias",
                "layout_constraintVertical_bias", "layout_constraintHorizontal_chainStyle",
                "layout_constraintVertical_chainStyle", "layout_constraintHorizontal_weight",
                "layout_constraintVertical_weight", "layout_goneMarginTop", "layout_goneMarginBottom",
                "layout_goneMarginLeft", "layout_goneMarginRight", "layout_goneMarginStart",
                "layout_goneMarginEnd", "layout_editor_absoluteX", "layout_editor_absoluteY",
                "layout_wrapBehaviorInParent", "layout_constrainedWidth", "layout_constrainedHeight",
                "layout_optimizationLevel", "layout_constraintTag"
        )));
        LAYOUT_PARAMS_MAP.put("GridLayout", new HashSet<>(Arrays.asList(
                "layout_row", "layout_column", "layout_rowSpan", "layout_columnSpan", "layout_gravity"
        )));
        LAYOUT_PARAMS_MAP.put("CoordinatorLayout", new HashSet<>(Arrays.asList(
                "layout_gravity", "layout_behavior", "layout_anchor", "layout_anchorGravity",
                "layout_keyline", "layout_insetEdge", "layout_dodgeInsetEdges"
        )));
        LAYOUT_PARAMS_MAP.put("DrawerLayout", new HashSet<>(Collections.singletonList("layout_gravity")));
        LAYOUT_PARAMS_MAP.put("TableLayout", new HashSet<>(Arrays.asList("layout_column", "layout_span", "layout_gravity")));
        LAYOUT_PARAMS_MAP.put("TableRow", new HashSet<>(Arrays.asList("layout_column", "layout_span", "layout_gravity")));

        // Containers that typically only support base + margin params
        Set<String> empty = Collections.emptySet();
        LAYOUT_PARAMS_MAP.put("ScrollView", empty);
        LAYOUT_PARAMS_MAP.put("HorizontalScrollView", empty);
        LAYOUT_PARAMS_MAP.put("ViewPager", empty);
        LAYOUT_PARAMS_MAP.put("ViewPager2", empty);
        LAYOUT_PARAMS_MAP.put("RecyclerView", empty);
        LAYOUT_PARAMS_MAP.put("ListView", empty);
        LAYOUT_PARAMS_MAP.put("GridView", empty);
        LAYOUT_PARAMS_MAP.put("AdapterViewFlipper", empty);
        LAYOUT_PARAMS_MAP.put("StackView", empty);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals("merge") || tag.equals("include") || tag.equals("fragment") ||
                tag.equals("requestFocus") || tag.equals("tag") || tag.equals("data") || tag.equals("variable")) {
            return;
        }

        Element parent = getParentLayout(element);
        if (parent == null) {
            return;
        }

        String parentTag = parent.getTagName();
        if (parentTag.equals("merge")) {
            parent = getParentLayout(parent);
            if (parent == null) return;
            parentTag = parent.getTagName();
        }

        String parentBaseClass = getBaseClassName(parentTag);
        Set<String> allowed = LAYOUT_PARAMS_MAP.get(parentBaseClass);
        if (allowed == null) {
            // Unknown parent layout (likely custom view), skip to avoid false positives
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String ns = attr.getNamespaceURI();
            if (ns != null && !ns.equals(SdkConstants.ANDROID_URI)) {
                continue;
            }

            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getNodeName();
                int colon = localName.indexOf(':');
                if (colon != -1) {
                    localName = localName.substring(colon + 1);
                }
            }

            if (localName.startsWith("layout_")) {
                if (BASE_LAYOUT_PARAMS.contains(localName) || MARGIN_LAYOUT_PARAMS.contains(localName)) {
                    continue;
                }
                if (!allowed.contains(localName)) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Invalid layout param in a `" + parentTag + "`: `" + attr.getNodeName() + "`");
                }
            }
        }
    }

    private static Element getParentLayout(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && !(parent instanceof Element)) {
            parent = parent.getParentNode();
        }
        return (Element) parent;
    }

    private static String getBaseClassName(String tag) {
        int dot = tag.lastIndexOf('.');
        return dot == -1 ? tag : tag.substring(dot + 1);
    }
}