package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<ArrayInfo>> arrays = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY,
                SdkConstants.TAG_ARRAY
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
            child = child.getNextSibling();
        }

        String qualifiers = "";
        if (context.getConfiguration() != null) {
            String q = context.getConfiguration().getQualifierString();
            if (q != null) {
                qualifiers = q;
            }
        }

        arrays.computeIfAbsent(name, k -> new ArrayList<>())
                .add(new ArrayInfo(context.getLocation(element), count, qualifiers));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, List<ArrayInfo>> entry : arrays.entrySet()) {
            List<ArrayInfo> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }

            int baselineCount = -1;
            for (ArrayInfo info : list) {
                if (info.qualifiers.isEmpty()) {
                    baselineCount = info.count;
                    break;
                }
            }
            if (baselineCount == -1) {
                baselineCount = list.get(0).count;
            }

            for (ArrayInfo info : list) {
                if (info.count != baselineCount) {
                    String message = String.format(
                            "Array `%s` has %d elements, but %d in the default configuration",
                            entry.getKey(), info.count, baselineCount);
                    context.report(ISSUE, info.location, message);
                }
            }
        }
        arrays.clear();
    }

    private static class ArrayInfo {
        final Location location;
        final int count;
        final String qualifiers;

        ArrayInfo(Location location, int count, String qualifiers) {
            this.location = location;
            this.count = count;
            this.qualifiers = qualifiers;
        }
    }
}