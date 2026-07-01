package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParam",
            "Obsolete layout params",
            "The given layout_param is not defined for the given layout, meaning it has "
                    + "no effect. This usually happens when you change the parent layout or "
                    + "move view code around without updating the layout params. This will "
                    + "cause useless attribute processing at runtime, and is misleading for "
                    + "others reading the layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Map<String, Set<String>> ALLOWED_PARAMS = new HashMap<>();

    static {
        Set<String> common = new HashSet<>(Arrays.asList(
                "layout_width",
                "layout_height",
                "layout_margin",
                "layout_marginLeft",
                "layout_marginTop",
                "layout_marginRight",
                "layout_marginBottom",
                "layout_marginStart",
                "layout_marginEnd",
                "layout_marginHorizontal",
                "layout_marginVertical"
        ));

        // LinearLayout
        Set<String> linear = new HashSet<>(common);
        linear.add("layout_gravity");
        linear.add("layout_weight");
        ALLOWED_PARAMS.put("LinearLayout", linear);
        ALLOWED_PARAMS.put("android.widget.LinearLayout", linear);
        ALLOWED_PARAMS.put("TableLayout", linear);
        ALLOWED_PARAMS.put("android.widget.TableLayout", linear);

        // FrameLayout
        Set<String> frame = new HashSet<>(common);
        frame.add("layout_gravity");
        ALLOWED_PARAMS.put("FrameLayout", frame);
        ALLOWED_PARAMS.put("android.widget.FrameLayout", frame);
        ALLOWED_PARAMS.put("ScrollView", frame);
        ALLOWED_PARAMS.put("android.widget.ScrollView", frame);
        ALLOWED_PARAMS.put("HorizontalScrollView", frame);
        ALLOWED_PARAMS.put("android.widget.HorizontalScrollView", frame);

        // RelativeLayout
        Set<String> relative = new HashSet<>(common);
        relative.addAll(Arrays.asList(
                "layout_above", "layout_below", "layout_toLeftOf", "layout_toRightOf",
                "layout_toStartOf", "layout_toEndOf",
                "layout_alignLeft", "layout_alignRight", "layout_alignTop", "layout_alignBottom",
                "layout_alignStart", "layout_alignEnd",
                "layout_alignParentLeft", "layout_alignParentRight", "layout_alignParentTop", "layout_alignParentBottom",
                "layout_alignParentStart", "layout_alignParentEnd",
                "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical",
                "layout_alignBaseline", "layout_alignWithParentIfMissing"
        ));
        ALLOWED_PARAMS.put("RelativeLayout", relative);
        ALLOWED_PARAMS.put("android.widget.RelativeLayout", relative);

        // TableRow
        Set<String> tableRow = new HashSet<>(common);
        tableRow.add("layout_column");
        tableRow.add("layout_span");
        ALLOWED_PARAMS.put("TableRow", tableRow);
        ALLOWED_PARAMS.put("android.widget.TableRow", tableRow);

        // GridLayout
        Set<String> grid = new HashSet<>(common);
        grid.addAll(Arrays.asList(
                "layout_row", "layout_rowSpan", "layout_column", "layout_columnSpan",
                "layout_rowWeight", "layout_columnWeight", "layout_gravity"
        ));
        ALLOWED_PARAMS.put("GridLayout", grid);
        ALLOWED_PARAMS.put("android.widget.GridLayout", grid);
        ALLOWED_PARAMS.put("androidx.gridlayout.widget.GridLayout", grid);

        // AbsoluteLayout
        Set<String> absolute = new HashSet<>(common);
        absolute.add("layout_x");
        absolute.add("layout_y");
        ALLOWED_PARAMS.put("AbsoluteLayout", absolute);
        ALLOWED_PARAMS.put("android.widget.AbsoluteLayout", absolute);
    }

    public ObsoleteLayoutParamsDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = parent.getTagName();

        Set<String> allowed = ALLOWED_PARAMS.get(parentTag);
        if (allowed == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String namespace = attr.getNamespaceURI();
            if (SdkConstants.ANDROID_URI.equals(namespace) || SdkConstants.AUTO_URI.equals(namespace)) {
                String localName = attr.getLocalName();
                if (localName != null && localName.startsWith("layout_")) {
                    if (!allowed.contains(localName)) {
                        String prefix = SdkConstants.AUTO_URI.equals(namespace) ? "app" : "android";
                        String message = String.format(
                                "The `%1$s:%2$s` attribute is not defined for the parent `%3$s` layout",
                                prefix, localName, parentTag
                        );
                        context.report(ISSUE, attr, context.getLocation(attr), message);
                    }
                }
            }
        }
    }
}