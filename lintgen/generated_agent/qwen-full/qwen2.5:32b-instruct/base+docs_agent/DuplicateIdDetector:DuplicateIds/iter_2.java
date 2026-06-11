package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout can cause unexpected behavior with `findViewById()`.",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.MANIFEST_SCOPE));

    @NonNull
    @Override
    public String[] getXmlTagNames() {
        return new String[]{SdkConstants.ANDROID_URI, SdkConstants.AUTO_URI};
    }

    @Override
    public void visitLayout(@NonNull Context context, @NonNull Element root) {
        Map<String, Location> idMap = new HashMap<>();
        for (Element child : getAllChildren(root)) {
            String idValue = getAttributeValue(child, "android:id");
            if (idValue != null && !idValue.isEmpty()) {
                Location location = context.getLocation(child);

                if (idMap.containsKey(idValue)) {
                    Location existingLocation = idMap.get(idValue);
                    context.report(ISSUE, child, location,
                            "Duplicate id found: %s", idValue)
                            .secondary(existingLocation, "First occurrence of the same id");
                } else {
                    idMap.put(idValue, location);
                }
            }
        }
    }

    private Iterable<Element> getAllChildren(Element element) {
        return new Iterable<Element>() {
            @Override
            public java.util.Iterator<Element> iterator() {
                return new java.util.Iterator<Element>() {
                    private Node next = element.getFirstChild();

                    @Override
                    public boolean hasNext() {
                        while (next != null && !(next instanceof Element)) {
                            next = next.getNextSibling();
                        }
                        return next != null;
                    }

                    @Override
                    public Element next() {
                        if (!hasNext()) {
                            throw new java.util.NoSuchElementException();
                        }
                        Element result = (Element) next;
                        next = next.getNextSibling();
                        while (next != null && !(next instanceof Element)) {
                            next = next.getNextSibling();
                        }
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    private String getAttributeValue(Element element, String attributeName) {
        return element.getAttribute(attributeName);
    }

    @NonNull
    @Override
    public List<String> getApplicableAttributes() {
        return List.of("android:id");
    }
}