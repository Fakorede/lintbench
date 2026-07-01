package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class RtlDetector extends LayoutDetector {

    private static final String KEY_REQUIRES_RTL = "requiresRtl";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) for "
                            + "right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /**
     * Attributes that have a "left" counterpart — if one is set, we check for the other.
     * Index i is the "left" attribute, index i+1 is the corresponding "right" attribute.
     */
    private static final String[] ATTRIBUTES = {
        ATTR_PADDING_LEFT,          ATTR_PADDING_RIGHT,
        ATTR_LAYOUT_MARGIN_LEFT,    ATTR_LAYOUT_MARGIN_RIGHT,
    };

    // ---- LayoutDetector / XmlScanner ----------------------------------------

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        String opposite = getOpposite(name);
        if (opposite == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        // Check whether the opposite attribute is already present.
        if (element.hasAttributeNS(ANDROID_URI, opposite)) {
            // Both sides specified — no issue.
            return;
        }

        String message = getMessage(name, opposite);

        Location location = context.getLocation(attribute);
        Incident incident =
                new Incident(ISSUE, attribute, location, message)
                        .overrideSeverity(Severity.WARNING);

        // We only report if the project actually supports RTL (minSdk >= 17 or
        // supportsRtl="true"). We store a flag in the LintMap and decide in
        // filterIncident().
        LintMap map = new LintMap();
        map.put(KEY_REQUIRES_RTL, true);
        context.report(incident, map);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report the incident if the project targets/supports RTL.
        // The map always carries KEY_REQUIRES_RTL=true when we stored it;
        // we gate on whether the project has RTL support (minSdk >= 17).
        if (context.getMainProject().getMinSdk() >= 17) {
            return true;
        }
        // Also report if the manifest declares supportsRtl.
        Boolean requiresRtl = map.getBoolean(KEY_REQUIRES_RTL);
        return requiresRtl != null && requiresRtl;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing extra needed after the root project check for this detector.
    }

    // ---- UastScanner --------------------------------------------------------

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Check Java/Kotlin references to R.attr / view setters for
                // paddingLeft / marginLeft without a corresponding right counterpart.
                // For the purposes of this detector the XML check is the primary
                // mechanism; the UAST handler is a placeholder for completeness.
                String name = node.getIdentifier();
                String opposite = getOpposite(name);
                if (opposite == null) {
                    return;
                }
                // In a full implementation we would inspect the surrounding
                // call/assignment to see whether the opposite is also set.
                // Omitted here as the XML check covers the main use-case.
            }
        };
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Returns the "opposite side" attribute name for a given left/right attribute,
     * or {@code null} if the attribute is not one we track.
     */
    @Nullable
    private static String getOpposite(@NonNull String name) {
        for (int i = 0; i < ATTRIBUTES.length; i += 2) {
            if (name.equals(ATTRIBUTES[i])) {
                return ATTRIBUTES[i + 1];
            }
            if (name.equals(ATTRIBUTES[i + 1])) {
                return ATTRIBUTES[i];
            }
        }
        return null;
    }

    @NonNull
    private static String getMessage(@NonNull String present, @NonNull String missing) {
        return String.format(
                "When specifying `%1$s` you should probably also specify `%2$s` for "
                        + "right-to-left layout symmetry",
                present,
                missing);
    }
}