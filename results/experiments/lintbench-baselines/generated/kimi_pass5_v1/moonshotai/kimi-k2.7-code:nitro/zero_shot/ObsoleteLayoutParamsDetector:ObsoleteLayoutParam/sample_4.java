package com.android.tools.lint.checks;

import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has no "
                    + "effect. This usually happens when you change the parent layout or move "
                    + "view code around without updating the layout params. This will cause "
                    + "useless attribute processing at runtime, and is misleading for others "
                    + "reading the layout so the parameter should be removed.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String LAYOUT_PREFIX = "layout_";

    private static final Set<String> MARGIN_PARAMS = ImmutableSet.of(
            "layout_margin",
            "layout_marginLeft",
            "layout_marginTop",
            "layout_marginRight",
            "layout_marginBottom",
            "layout_marginStart",
            "layout_marginEnd",
            "layout_marginHorizontal",
            "layout_marginVertical");

    private static final Map<String, Set<String>> VALID_PARAMS;

    static {
        ImmutableMap.Builder<String, Set<String>> builder = ImmutableMap.builder();

        builder.put("LinearLayout", params(
                "layout_width",
                "layout_height",
                "layout_weight",
                "layout_gravity"));

        builder.put("FrameLayout", params(
                "layout_width",
                "layout_height",
                "layout_gravity"));

        builder.put("RelativeLayout", params(
                "layout_width",
                "layout_height",
                "layout_above",
                "layout_alignBaseline",
                "layout_alignBottom",
                "layout_alignEnd",
                "layout_alignLeft",
                "layout_alignParentBottom",
                "layout_alignParentEnd",
                "layout_alignParentLeft",
                "layout_alignParentRight",
                "layout_alignParentStart",
                "layout_alignParentTop",
                "layout_alignRight",
                "layout_alignStart",
                "layout_alignTop",
                "layout_alignWithParentIfMissing",
                "layout_below",
                "layout_centerHorizontal",
                "layout_centerInParent",
                "layout_centerVertical",
                "layout_toEndOf",
                "layout_toLeftOf",
                "layout_toRightOf",
                "layout_toStartOf"));

        builder.put("GridLayout", params(
                "layout_width",
                "layout_height",
                "layout_gravity",
                "layout_column",
                "layout_columnSpan",
                "layout_columnWeight",
                "layout_row",
                "layout_rowSpan",
                "layout_rowWeight"));

        builder.put("CoordinatorLayout", params(
                "layout_width",
                "layout_height",
                "layout_behavior",
                "layout_anchor",
                "layout_anchorGravity",
                "layout_dodgeInsetEdges",
                "layout_insetEdge",
                "layout_keyline"));

        builder.put("ConstraintLayout", params(
                "layout_width",
                "layout_height",
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
                "layout_constraintDimensionRatio",
                "layout_constraintWidth_default",
                "layout_constraintHeight_default",
                "layout_constraintWidth_min",
                "layout_constraintWidth_max",
                "layout_constraintWidth_percent",
                "layout_constraintHeight_min",
                "layout_constraintHeight_max",
                "layout_constraintHeight_percent",
                "layout_constraintCircle",
                "layout_constraintCircleRadius",
                "layout_constraintCircleAngle",
                "layout_editor_absoluteX",
                "layout_editor_absoluteY",
                "layout_goneMarginLeft",
                "layout_goneMarginTop",
                "layout_goneMarginRight",
                "layout_goneMarginBottom",
                "layout_goneMarginStart",
                "layout_goneMarginEnd",
                "layout_chainUseRtl",
                "layout_constraintHorizontal_chainStyle",
                "layout_constraintVertical_chainStyle",
                "layout_constraintHorizontal_weight",
                "layout_constraintVertical_weight",
                "layout_constrainedWidth",
                "layout_constrainedHeight",
                "layout_constraintWidth",
                "layout_constraintHeight",
                "layout_wrapBehaviorInParent",
                "layout_constraintTag"));

        VALID_PARAMS = builder.build();
    }

    private static Set<String> params(String... extras) {
        return ImmutableSet.<String>builder()
                .addAll(MARGIN_PARAMS)
                .add(extras)
                .build();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }

        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        int colonIndex = parentTag.indexOf(':');
        if (colonIndex != -1) {
            parentTag = parentTag.substring(colonIndex + 1);
        }
        int dotIndex = parentTag.lastIndexOf('.');
        if (dotIndex != -1) {
            parentTag = parentTag.substring(dotIndex + 1);
        }

        Set<String> validParams = VALID_PARAMS.get(parentTag);
        if (validParams == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, count = attributes.getLength(); i < count; i++) {
            Attr attr = (Attr) attributes.item(i);
            if (TOOLS_URI.equals(attr.getNamespaceURI())) {
                continue;
            }

            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }

            if (!name.startsWith(LAYOUT_PREFIX)) {
                continue;
            }

            if (!validParams.contains(name)) {
                String message = "Obsolete layout param `" + name + "` in a `" + parentTag + "`";
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }
}