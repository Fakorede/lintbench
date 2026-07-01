package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            3,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ATTR_PADDING_HORIZONTAL = "paddingHorizontal";
    private static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";

    @Override
    public Collection<String> getApplicableElements() {
        return ResourceXmlDetector.ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap nodeMap = element.getAttributes();
        Set<String> names = new HashSet<>();
        Map<String, Attr> attrs = new HashMap<>();

        for (int i = 0, n = nodeMap.getLength(); i < n; i++) {
            Node node = nodeMap.item(i);
            if (!(node instanceof Attr)) {
                continue;
            }
            Attr attr = (Attr) node;
            if (!SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())) {
                continue;
            }
            String name = attr.getLocalName();
            if (name == null || name.isEmpty()) {
                String qName = attr.getName();
                int colon = qName.indexOf(':');
                name = colon != -1 ? qName.substring(colon + 1) : qName;
            }
            names.add(name);
            attrs.put(name, attr);
        }

        checkPair(context, element, names, attrs,
                SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_RIGHT,
                SdkConstants.ATTR_PADDING, ATTR_PADDING_HORIZONTAL);

        checkPair(context, element, names, attrs,
                SdkConstants.ATTR_PADDING_START, SdkConstants.ATTR_PADDING_END,
                SdkConstants.ATTR_PADDING, ATTR_PADDING_HORIZONTAL);

        checkPair(context, element, names, attrs,
                SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                SdkConstants.ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_HORIZONTAL);

        checkPair(context, element, names, attrs,
                SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_END,
                SdkConstants.ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_HORIZONTAL);
    }

    private static void checkPair(XmlContext context, Element element, Set<String> names,
            Map<String, Attr> attrs, String side1, String side2, String... covering) {
        boolean hasSide1 = names.contains(side1);
        boolean hasSide2 = names.contains(side2);
        if (hasSide1 == hasSide2) {
            return;
        }
        for (String cover : covering) {
            if (names.contains(cover)) {
                return;
            }
        }

        String present = hasSide1 ? side1 : side2;
        String missing = hasSide1 ? side2 : side1;
        Attr attr = attrs.get(present);

        String message = "To support right-to-left layouts, consider adding `android:"
                + missing + "` alongside `android:" + present + "`";
        Location location = attr != null ? context.getLocation(attr) : context.getLocation(element);
        context.report(ISSUE, attr != null ? attr : element, location, message);
    }
}