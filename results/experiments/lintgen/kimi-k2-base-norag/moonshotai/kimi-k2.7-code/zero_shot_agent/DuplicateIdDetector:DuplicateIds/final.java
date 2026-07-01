package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;

public class DuplicateIdDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Map<String, List<Attr>> mIds;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@+id/")) {
            return;
        }

        String id = value.substring(5);
        List<Attr> list = mIds.get(id);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(id, list);
        }
        list.add(attribute);
    }

    @Override
    public void afterCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, List<Attr>> entry : mIds.entrySet()) {
            List<Attr> list = entry.getValue();
            if (list.size() > 1) {
                String id = entry.getKey();
                String message =
                        String.format(
                                "Duplicate id @+id/%1$s, already defined earlier in this layout",
                                id);
                for (int i = 1; i < list.size(); i++) {
                    Attr attribute = list.get(i);
                    xmlContext.report(
                            ISSUE,
                            attribute,
                            xmlContext.getValueLocation(attribute),
                            message);
                }
            }
        }
        mIds = null;
    }
}