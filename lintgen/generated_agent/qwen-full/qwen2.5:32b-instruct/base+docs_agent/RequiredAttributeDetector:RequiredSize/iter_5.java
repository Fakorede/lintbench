package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredAttributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute.",
            "Views in Android layouts need to have their size specified explicitly. If you fail to do so, a runtime exception will be thrown.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.XML_FILE)));

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public boolean appliesToResourceTypes(ResourceType type) {
        return type == ResourceType.LAYOUT;
    }

    @Override
    public void visitXml(@NonNull XmlContext context, @NonNull Element element) {
        if (!element.getTagName().equals(SdkConstants.TAG_GRID_LAYOUT)) {
            checkViewAttributes(context, element);
        }
    }

    private void checkViewAttributes(@NonNull XmlContext context, @NonNull Element element) {
        Location location = Location.create(element);

        boolean hasWidth = false;
        boolean hasHeight = false;

        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            String attrName = element.getAttributeNodeNS(null, "layout_width") != null ? "layout_width" :
                              element.getAttributeNodeNS(null, "layout_height") != null ? "layout_height" : "";

            if ("layout_width".equals(attrName)) {
                hasWidth = true;
            } else if ("layout_height".equals(attrName)) {
                hasHeight = true;
            }
        }

        if (!hasWidth) {
            context.report(ISSUE, location, "View must specify an explicit `layout_width` attribute");
        }

        if (!hasHeight) {
            context.report(ISSUE, location, "View must specify an explicit `layout_height` attribute");
        }
    }
}