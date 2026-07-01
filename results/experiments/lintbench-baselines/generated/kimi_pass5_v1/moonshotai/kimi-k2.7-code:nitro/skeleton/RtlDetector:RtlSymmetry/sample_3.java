package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    private static final String PADDING_LEFT = "paddingLeft";
    private static final String PADDING_RIGHT = "paddingRight";
    private static final String LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String LAYOUT_MARGIN_RIGHT = "layout_marginRight";

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "Specifying padding or margin on only one side of a layout can lead to "
                            + "asymmetric layouts in right-to-left (RTL) locales. To maintain "
                            + "symmetry, you should specify both the left and right counterpart "
                            + "attributes (for example, both paddingLeft and paddingRight, or "
                            + "both layout_marginLeft and layout_marginRight).",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, List<AttrHolder>>> mAttributes = new HashMap<>();

    private static class AttrHolder {
        final XmlContext context;
        final Attr attribute;

        AttrHolder(XmlContext context, Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map<String, List<AttrHolder>> fileMap : mAttributes.values()) {
            for (List<AttrHolder> elementAttrs : fileMap.values()) {
                List<String> present = new ArrayList<>(elementAttrs.size());
                for (AttrHolder holder : elementAttrs) {
                    present.add(holder.attribute.getLocalName());
                }

                for (AttrHolder holder : elementAttrs) {
                    String name = holder.attribute.getLocalName();
                    String counterpart = getCounterpart(name);
                    if (counterpart != null && !present.contains(counterpart)) {
                        String message = String.format(
                                "To maintain RTL symmetry, you should also specify %s when specifying %s",
                                counterpart, name);
                        holder.context.report(
                                ISSUE,
                                holder.context.getLocation(holder.attribute),
                                message);
                    }
                }
            }
        }
        mAttributes.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                PADDING_LEFT,
                PADDING_RIGHT,
                LAYOUT_MARGIN_LEFT,
                LAYOUT_MARGIN_RIGHT
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (isApplicable(name)) {
            String file = context.file.getPath();
            Element owner = attribute.getOwnerElement();
            String elementKey = owner.getTagName() + "@" + System.identityHashCode(owner);

            Map<String, List<AttrHolder>> fileMap = mAttributes.get(file);
            if (fileMap == null) {
                fileMap = new HashMap<>();
                mAttributes.put(file, fileMap);
            }

            List<AttrHolder> list = fileMap.get(elementKey);
            if (list == null) {
                list = new ArrayList<>();
                fileMap.put(elementKey, list);
            }

            list.add(new AttrHolder(context, attribute));
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No Java-side checks for RtlSymmetry.
            }
        };
    }

    private static boolean isApplicable(@NonNull String name) {
        return PADDING_LEFT.equals(name)
                || PADDING_RIGHT.equals(name)
                || LAYOUT_MARGIN_LEFT.equals(name)
                || LAYOUT_MARGIN_RIGHT.equals(name);
    }

    private static String getCounterpart(@NonNull String name) {
        if (PADDING_LEFT.equals(name)) {
            return PADDING_RIGHT;
        }
        if (PADDING_RIGHT.equals(name)) {
            return PADDING_LEFT;
        }
        if (LAYOUT_MARGIN_LEFT.equals(name)) {
            return LAYOUT_MARGIN_RIGHT;
        }
        if (LAYOUT_MARGIN_RIGHT.equals(name)) {
            return LAYOUT_MARGIN_LEFT;
        }
        return null;
    }
}