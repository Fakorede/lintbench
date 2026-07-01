package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ArraySizeDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ArraySizeDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the "
                    + "same number of elements as the original array. When adding or removing "
                    + "elements to an array, it is easy to forget to update all the locales, and "
                    + "this lint warning finds inconsistencies like these.\n\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private Map<String, Map<String, ArrayInfo>> mArrayCounts;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY,
                SdkConstants.TAG_ARRAY);
    }

    @Override
    public void beforeCheckProject(Context context) {
        mArrayCounts = new HashMap<>();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        FolderConfiguration config = FolderConfiguration.getConfig(context.file.getParentFile());
        if (config == null) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String key = element.getTagName() + "/" + name;
        String configKey = config.getQualifierString();

        Map<String, ArrayInfo> perConfig = mArrayCounts.get(key);
        if (perConfig == null) {
            perConfig = new HashMap<>();
            mArrayCounts.put(key, perConfig);
        }
        perConfig.put(configKey, new ArrayInfo(config, count, context.getLocation(element)));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, Map<String, ArrayInfo>> entry : mArrayCounts.entrySet()) {
            Collection<ArrayInfo> infos = entry.getValue().values();
            if (infos.size() < 2) {
                continue;
            }

            Map<Integer, Integer> frequencies = new HashMap<>();
            for (ArrayInfo info : infos) {
                frequencies.merge(info.count, 1, Integer::sum);
            }

            int expected = -1;
            int maxFrequency = -1;
            for (Map.Entry<Integer, Integer> freq : frequencies.entrySet()) {
                if (freq.getValue() > maxFrequency) {
                    maxFrequency = freq.getValue();
                    expected = freq.getKey();
                }
            }

            boolean inconsistent = false;
            for (ArrayInfo info : infos) {
                if (info.count != expected) {
                    inconsistent = true;
                    break;
                }
            }
            if (!inconsistent) {
                continue;
            }

            for (ArrayInfo info : infos) {
                if (info.count != expected) {
                    String configName = info.config.getQualifierString();
                    if (configName.isEmpty()) {
                        configName = "default";
                    }
                    String message = String.format(
                            "Array `%1$s` has %2$d items in the `%3$s` configuration, but %4$d items in other configurations",
                            entry.getKey(), info.count, configName, expected);
                    context.report(ISSUE, info.location, message);
                }
            }
        }

        mArrayCounts = null;
    }

    private static class ArrayInfo {
        final FolderConfiguration config;
        final int count;
        final Location location;

        ArrayInfo(FolderConfiguration config, int count, Location location) {
            this.config = config;
            this.count = count;
            this.location = location;
        }
    }
}