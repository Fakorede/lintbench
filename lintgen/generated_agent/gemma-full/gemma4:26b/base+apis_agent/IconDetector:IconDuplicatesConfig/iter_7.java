package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;

public class IconDetector extends XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IdenticalIconAcrossConfigurations",
            "Identical icon across configurations",
            "The same resource content is provided in multiple configuration folders (e.g., drawable-hdpi and drawable-xhdpi). This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            com.android.tools.lint.detector.api.Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    private final Map<String, List<File>> resourceToFilesCache = new ConcurrentHashMap<>();
    private File cachedResDir = null;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:src");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || (!value.startsWith("@drawable/") && !value.startsWith("@mipmap/"))) {
            return;
        }

        String resourceName = value.substring(value.indexOf("/") + 1);
        int dotIndex = resourceName.indexOf('.');
        if (dot0 != -1) {
            resourceName = resourceName.substring(0, dotIndex);
        }

        checkDuplicates(context, attribute, resourceName);
    }

    private void checkDuplicates(XmlContext context, Attr attribute, String resourceName) {
        try {
            if (cachedResDir == null) {
                for (File file : context.getFiles()) {
                    String path = file.getAbsolutePath();
                    int resIndex = path.indexOf("/res/");
                    if (resIndex != -1) {
                        cachedResDir = new File(path.substring(0, resIndex + 5));
                        break;
                    }
                }
            }

            if (cachedResDir == null || !cachedResDir.exists()) {
                return;
            }

            List<File> matches = resourceToFilesCache.get(resourceName);
            if (matches == null) {
                matches = new ArrayList<>();
                findMatchingFiles(cachedResDir, resourceName, matches);
                resourceToFilesCache.put(resourceName, matches);
            }

            if (matches.size() < 2) {
                return;
            }

            Map<String, List<File>> md5Groups = new HashMap<>();
            for (File f : matches) {
                String hash = getMd5(f);
                md5Groups.computeIfAbsent(hash, k -> new ArrayList<>()).add(f);
            }

            for (Map.Entry<String, List<File>> entry : md5Groups.entrySet()) {
                if (entry.getValue().size() > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        sb.append(entry.getValue().get(i).getName());
                        if (i < entry.getValue().size() - 1) sb.append(", ");
                    }
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Identical resource content found in: " + sb.toString(),
                            null
                    );
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore errors during scanning to prevent breaking the lint run
        }
    }

    private void findMatchingFiles(File dir, String name, List<File> matches) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findMatchingFiles(f, name, matches);
            } else {
                String fileName = f.getName();
                if ((fileName.startsWith(name + ".") || fileName.equals(name)) &&
                        (fileName.endsWith(".png") || fileName.endsWith(".webp") ||
                                fileName.endsWith(".jpg") || fileName.endsWith(".xml"))) {
                    matches.add(f);
                }
            }
        }
    }

    private String getMd5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        String name = folderType.getName().toLowerCase();
        return name.contains("layout") || name.contains("values");
    }
}