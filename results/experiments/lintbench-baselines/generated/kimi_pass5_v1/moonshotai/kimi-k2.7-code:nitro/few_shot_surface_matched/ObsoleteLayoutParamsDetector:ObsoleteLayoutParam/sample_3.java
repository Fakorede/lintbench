package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ObsoleteLayoutParamsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LAYOUT_PREFIX = "layout_";

    private static final Map<String, Set<String>> sAllowed;

    static {
        Map<String, Set<String>> map = new HashMap<>();

        Set<String> base = new HashSet<>();
        base.add("layout_width");
        base.add("layout_height");

        Set<String> frame = new HashSet<>(base);
        frame.add("layout_gravity");
        addMargins(frame);
        map.put("FrameLayout", frame);
        map.put("android.widget.FrameLayout", frame);

        Set<String> linear = new HashSet<>(base);
        linear.add("layout_weight");
        linear.add("layout_gravity");
        addMargins(linear);
        map.put("LinearLayout", linear);
        map.put("android.widget.LinearLayout", linear);

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
        addMargins(relative);
        map.put("RelativeLayout", relative);
        map.put("android.widget.RelativeLayout", relative);

        Set<String> grid = new HashSet<>(base);
        grid.add("layout_column");
        grid.add("layout_columnSpan");
        grid.add("layout_row");
        grid.add("layout_rowSpan");
        grid.add("layout_gravity");
        addMargins(grid);
        map.put("GridLayout", grid);
        map.put("android.widget.GridLayout", grid);

        Set<String> tableRow = new HashSet<>(base);
        tableRow.add("layout_column");
        tableRow.add("layout_span");
        addMargins(tableRow);
        map.put("TableRow", tableRow);
        map.put("android.widget.TableRow", tableRow);

        Set<String> drawer = new HashSet<>(base);
        drawer.add("layout_gravity");
        addMargins(drawer);
        map.put("androidx.drawerlayout.widget.DrawerLayout", drawer);
        map.put("android.support.v4.widget.DrawerLayout", drawer);

        Set<String> coordinator = new HashSet<>(base);
        coordinator.add("layout_anchor");
        coordinator.add("layout_anchorGravity");
        coordinator.add("layout_behavior");
        coordinator.add("layout_dodgeInsetEdges");
        coordinator.add("layout_insetEdge");
        coordinator.add("layout_keyline");
        addMargins(coordinator);
        map.put("androidx.coordinatorlayout.widget.CoordinatorLayout", coordinator);
        map.put("android.support.design.widget.CoordinatorLayout", coordinator);

        Set<String> constraint = new HashSet<>(base);
        constraint.add("layout_constraintBottom_toBottomOf");
        constraint.add("layout_constraintBottom_toTopOf");
        constraint.add("layout_constraintEnd_toEndOf");
        constraint.add("layout_constraintEnd_toStartOf");
        constraint.add("layout_constraintStart_toEndOf");
        constraint.add("layout_constraintStart_toStartOf");
        constraint.add("layout_constraintLeft_toLeftOf");
        constraint.add("layout_constraintLeft_toRightOf");
        constraint.add("layout_constraintRight_toLeftOf");
        constraint.add("layout_constraintRight_toRightOf");
        constraint.add("layout_constraintTop_toBottomOf");
        constraint.add("layout_constraintTop_toTopOf");
        constraint.add("layout_constraintHorizontal_bias");
        constraint.add("layout_constraintVertical_bias");
        constraint.add("layout_constraintDimensionRatio");
        constraint.add("layout_constraintHeight_default");
        constraint.add("layout_constraintHeight_max");
        constraint.add("layout_constraintHeight_min");
        constraint.add("layout_constraintHeight_percent");
        constraint.add("layout_constraintWidth_default");
        constraint.add("layout_constraintWidth_max");
        constraint.add("layout_constraintWidth_min");
        constraint.add("layout_constraintWidth_percent");
        constraint.add("layout_constraintHorizontal_chainStyle");
        constraint.add("layout_constraintVertical_chainStyle");
        constraint.add("layout_constraintHorizontal_weight");
        constraint.add("layout_constraintVertical_weight");
        constraint.add("layout_constraintGuide_begin");
        constraint.add("layout_constraintGuide_end");
        constraint.add("layout_constraintGuide_percent");
        constraint.add("layout_editor_absoluteX");
        constraint.add("layout_editor_absoluteY");
        constraint.add("layout_goneMarginBottom");
        constraint.add("layout_goneMarginEnd");
        constraint.add("layout_goneMarginLeft");
        constraint.add("layout_goneMarginRight");
        constraint.add("layout_goneMarginStart");
        constraint.add("layout_goneMarginTop");
        addMargins(constraint);
        map.put("androidx.constraintlayout.widget.ConstraintLayout", constraint);
        map.put("android.support.constraint.ConstraintLayout", constraint);

        sAllowed = Collections.unmodifiableMap(map);
    }

    private static void addMargins(Set<String> set) {
        set.add("layout_margin");
        set.add("layout_marginLeft");
        set.add("layout_marginRight");
        set.add("layout_marginTop");
        set.add("layout_marginBottom");
        set.add("layout_marginStart");
        set.add("layout_marginEnd");
        set.add("layout_marginHorizontal");
        set.add("layout_marginVertical");
    }

    private final Map<Element, List<ObsoleteAttr>> mObsoleteAttributes = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // No per-element processing is required; obsolete params are caught in visitAttribute.
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (name == null || !name.startsWith(LAYOUT_PREFIX)) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        Element parent = getParentElement(owner);
        if (parent == null) {
            return;
        }

        Set<String> allowed = sAllowed.get(parent.getTagName());
        if (allowed == null || allowed.contains(name)) {
            return;
        }

        List<ObsoleteAttr> list = mObsoleteAttributes.get(owner);
        if (list == null) {
            list = new ArrayList<>();
            mObsoleteAttributes.put(owner, list);
        }
        list.add(new ObsoleteAttr(context, owner, attribute));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<Element, List<ObsoleteAttr>> entry : mObsoleteAttributes.entrySet()) {
            Element owner = entry.getKey();
            for (ObsoleteAttr obsolete : entry.getValue()) {
                obsolete.context.report(
                        ISSUE,
                        obsolete.attribute,
                        obsolete.context.getLocation(obsolete.attribute),
                        "Invalid layout param in a `"
                                + owner.getTagName()
                                + "`: "
                                + obsolete.attribute.getLocalName());
            }
        }
        mObsoleteAttributes.clear();
    }

    private static Element getParentElement(Element element) {
        if (element.getParentNode() instanceof Element) {
            return (Element) element.getParentNode();
        }
        return null;
    }

    private static class ObsoleteAttr {
        final XmlContext context;
        final Element owner;
        final Attr attribute;

        ObsoleteAttr(XmlContext context, Element owner, Attr attribute) {
            this.context = context;
            this.owner = owner;
            this.attribute = attribute;
        }
    }
}