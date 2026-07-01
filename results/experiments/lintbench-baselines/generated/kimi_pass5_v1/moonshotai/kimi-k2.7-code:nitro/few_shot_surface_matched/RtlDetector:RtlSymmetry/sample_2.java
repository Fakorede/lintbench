package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String LEFT = "Left";
    private static final String RIGHT = "Right";

    private static final Collection<String> ATTRIBUTES =
            Arrays.asList(
                    ATTR_PADDING_LEFT,
                    ATTR_PADDING_RIGHT,
                    ATTR_LAYOUT_MARGIN_LEFT,
                    ATTR_LAYOUT_MARGIN_RIGHT);

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) "
                            + "for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static class ReferenceInfo {
        final String name;
        final UElement node;
        final Location location;

        ReferenceInfo(String name, UElement node, Location location) {
            this.name = name;
            this.node = node;
            this.location = location;
        }
    }

    private final Map<String, List<ReferenceInfo>> mReferences = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }
        String paired = getPairedName(name);
        if (paired == null) {
            return;
        }
        Element element = attribute.getOwnerElement();
        if (hasAttribute(element, paired)) {
            return;
        }
        String message =
                String.format(
                        "To support right-to-left layouts, consider adding `%1$s` in addition to `%2$s`",
                        paired, name);
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {
                String name = expression.getIdentifier();
                if (name == null || !isRtlName(name)) {
                    return;
                }
                String paired = getPairedName(name);
                if (paired == null) {
                    return;
                }
                String path = context.getFile().getPath();
                List<ReferenceInfo> list = mReferences.get(path);
                if (list == null) {
                    list = new ArrayList<>();
                    mReferences.put(path, list);
                }
                list.add(new ReferenceInfo(name, expression, context.getLocation(expression)));
            }
        };
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<ReferenceInfo>> entry : mReferences.entrySet()) {
            List<ReferenceInfo> refs = entry.getValue();
            Set<String> names = new HashSet<>();
            for (ReferenceInfo ref : refs) {
                names.add(ref.name);
            }
            for (ReferenceInfo ref : refs) {
                String paired = getPairedName(ref.name);
                if (paired != null && names.contains(paired)) {
                    continue;
                }
                String message =
                        String.format(
                                "To support right-to-left layouts, consider also using `%1$s` in addition to `%2$s`",
                                paired, ref.name);
                context.report(ISSUE, ref.node, ref.location, message);
            }
        }
        mReferences.clear();
    }

    private static boolean hasAttribute(@NonNull Element element, @NonNull String name) {
        return element.hasAttribute(name) || element.hasAttributeNS(ANDROID_URI, name);
    }

    private static boolean isRtlName(@NonNull String name) {
        String lower = name.toLowerCase();
        return lower.contains("padding") || lower.contains("margin");
    }

    @Nullable
    private static String getPairedName(@NonNull String name) {
        if (name.endsWith(LEFT)) {
            return name.substring(0, name.length() - LEFT.length()) + RIGHT;
        } else if (name.endsWith(RIGHT)) {
            return name.substring(0, name.length() - RIGHT.length()) + LEFT;
        }
        return null;
    }
}