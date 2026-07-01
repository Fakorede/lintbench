package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue DUPLICATE_DEFINITIONS =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders;"
                            + " that's how string translations are done, for example. However,"
                            + " defining the same resource more than once in the same resource"
                            + " folder is likely an error, for example attempting to add a new"
                            + " resource without realizing that the name is already used, and so"
                            + " on.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private java.util.Map<String, java.util.List<org.w3c.dom.Attr>> mNames;

    @Override
    public boolean appliesTo(com.android.ide.common.resources.ResourceFolderType folderType) {
        return folderType != com.android.ide.common.resources.ResourceFolderType.RAW
                && folderType != com.android.ide.common.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList(com.android.SdkConstants.ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mNames = new java.util.HashMap<>();
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        String type = getResourceType(element);
        if (type == null || type.isEmpty() || !isValidResourceType(type)) {
            return;
        }

        String key = type + '/' + name;
        java.util.List<org.w3c.dom.Attr> list = mNames.get(key);
        if (list == null) {
            list = new java.util.ArrayList<>();
            mNames.put(key, list);
        }
        list.add(attribute);
    }

    @Override
    public void afterCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (java.util.Map.Entry<String, java.util.List<org.w3c.dom.Attr>> entry :
                mNames.entrySet()) {
            java.util.List<org.w3c.dom.Attr> list = entry.getValue();
            if (list.size() > 1) {
                String message =
                        "Duplicate definition of `" + entry.getKey() + "`";
                for (int i = 1; i < list.size(); i++) {
                    org.w3c.dom.Attr attr = list.get(i);
                    Location location = xmlContext.getLocation(attr);
                    xmlContext.report(DUPLICATE_DEFINITIONS, attr, location, message);
                }
            }
        }
        mNames = null;
    }

    private static String getResourceType(org.w3c.dom.Element element) {
        String tag = element.getTagName();

        if (tag.equals(com.android.SdkConstants.TAG_ITEM)) {
            String type = element.getAttribute(com.android.SdkConstants.ATTR_TYPE);
            if (type != null && !type.isEmpty()) {
                return type;
            }
            return null;
        }

        if (tag.equals(com.android.SdkConstants.TAG_ENUM)
                || tag.equals(com.android.SdkConstants.TAG_FLAG)) {
            return null;
        }

        if (tag.equals(com.android.SdkConstants.TAG_STRING_ARRAY)
                || tag.equals(com.android.SdkConstants.TAG_INTEGER_ARRAY)) {
            return "array";
        }

        if (tag.equals(com.android.SdkConstants.TAG_DECLARE_STYLEABLE)) {
            return "styleable";
        }

        return tag;
    }

    private static boolean isValidResourceType(String type) {
        switch (type) {
            case "anim":
            case "animator":
            case "array":
            case "attr":
            case "bool":
            case "color":
            case "dimen":
            case "drawable":
            case "font":
            case "fraction":
            case "id":
            case "integer":
            case "interpolator":
            case "layout":
            case "menu":
            case "mipmap":
            case "navigation":
            case "plurals":
            case "raw":
            case "string":
            case "style":
            case "styleable":
            case "transition":
            case "xml":
                return true;
            default:
                return false;
        }
    }
}