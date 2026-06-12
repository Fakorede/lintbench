package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IdenticalIconAcrossConfigurations",
            "Identical icon across configurations",
            "The same resource content is provided in multiple configuration folders (e.g., drawable-hdpi and drawable-xhdpi). This usually indicates a copy-paste error where the icon was not resized for the specific density.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILES_ONLY)
    );

    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList(".png", ".webp", ".jpg", ".jpeg"));
    private final Map<String, String> fileHashCache = new ConcurrentHashMap<>();
    private final Map<String, List<File>> resourceMatchesCache = new ConcurrentHashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("android:src", "android:background", "android:drawableTop", "android:drawableLeft", "android:drawableRight", "android:drawableBottom");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Extract resource name from @drawable/name or @mipmap/name
        String resourceName = null;
        int slashIndex = value.indexOf('/');
        if (slashIndex != -1) {
            resourceName = value.substring(slashIndex + 1);
        } else {
            resourceName = value.substring(1);
        }

        // Remove extension if present (e.g., @drawable/icon.png -> icon)
        int dotIndex = resourceName.indexOf('.');
        if (dotIndex != -1) {
            resourceName = resourceName.substring(0, dotIndex);
        }

        if (resourceName == null || resourceName.isEmpty()) {
            return;
        }

        try {
            File resDir = findResDir(context);
            if (resDir == null) return;

            List<File> matches = resourceMatchesCache.get(resourceName);
            if (matches == null) {
                matches = new ArrayList<>();
                findMatchingFiles(resDir, resourceName, matches);
                if (!matches.isEmpty()) {
                    resourceMatchesCache.put(resourceName, matches);
                }
            }

            if (matches != null && matches.size() > 1) {
                Map<String, List<File>> hashGroups = new HashMap<>();
                for (File f : matches) {
                    String hash = getFileHash(f);
                    if (hash != null) {
                        hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(f);
                    }
                }

                for (Map.Entry<String, List<File>> entry : hashGroups.entrySet()) {
                    List<File> duplicates = entry.getValue();
                    if (duplicates.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < duplicates.size(); i++) {
                            sb.append(duplicates.get(i).getParentFile().getName())
                              .append("/")
                              .append(duplicates.get(i).getName());
                            if (i < duplicates.size() - 1) sb.append(", ");
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
            }
        } catch (Exception e) {
            // Silently fail to avoid breaking the lint run
        }
    }

    private File findResDir(XmlContext context) {
        try {
            File root = context.getProject().getRootDir();
            File current = root;
            while (current != null) {
                File res = new File(current, "res");
                if (res.exists() && res.isDirectory()) {
                    return res;
                }
                current = current.getParentFile();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void findMatchingFiles(File resDir, String name, List<File> matches) {
        if (!resDir.exists()) return;
        searchRecursive(resDir, name, matches);
    }

    private void searchRecursive(File dir, String name, List<File> matches) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                searchRecursive(f, name, matches);
            } else {
                String fileName = f.getName();
                if (fileName.startsWith(name + ".") || fileName.equals(name)) {
                    String ext = "";
                    int dot = fileName.lastIndexOf('.');
                    if (dot != -1) {
                        ext = fileName.substring(dot).toLowerCase();
                    }
                    if (EXTENSIONS.contains(ext) || ext.isEmpty()) {
                        matches.add(f);
                    }
                }
            }
        }
    }

    private String getFileHash(File file) {
        if (fileHashCache.containsKey(file.getAbsolutePath())) {
            return fileHashCache.get(file.getAbsolutePath());
        }
        try {
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
            String hash = sb.toString();
            fileHashCache.put(file.getAbsolutePath(), hash);
            return hash;
        } catch (Exception e) {
            return null;
        }
    }
}