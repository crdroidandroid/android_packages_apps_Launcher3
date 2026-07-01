package com.android.launcher3.icons.pack;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class IconPackParser {
    private static final String TAG = "IconPackParser";

    static IconPack.Data parsePackage(PackageManager pm, Resources res, String pkg)
            throws IOException, XmlPullParserException {
        IconPack.Data iconPack = new IconPack.Data();

        int resId = res.getIdentifier("appfilter", "xml", pkg);
        if (resId != 0) {
            XmlResourceParser parseXml = pm.getXml(pkg, resId, null);
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.getEventType() == XmlPullParser.START_TAG) {
                    switch (parseXml.getName()) {
                        case "item":
                            addItem(parseXml, iconPack);
                            break;
                        case "calendar":
                            addCalendar(parseXml, iconPack);
                            break;
                        case "iconback":
                            addImgsTo(res, pkg, parseXml, iconPack.iconBacks);
                            break;
                        case "iconmask":
                            addImgsTo(res, pkg, parseXml, iconPack.iconMasks);
                            break;
                        case "iconupon":
                            addImgsTo(res, pkg, parseXml, iconPack.iconUpons);
                            break;
                        case "scale":
                            setScale(parseXml, iconPack);
                            break;
                        case "dynamic-clock":
                            addClock(res, pkg, parseXml, iconPack);
                            break;
                    }
                }
            }
        }

        return iconPack;
    }

    static List<IconPack.IconEntry> parseAllEntries(PackageManager pm, Resources res, String pkg)
            throws IOException, XmlPullParserException {
        LinkedHashSet<String> catalogNames = new LinkedHashSet<>();
        collectDrawableNames(pm, res, pkg, "drawable", catalogNames);
        if (!catalogNames.isEmpty()) {
            return toEntries(res, pkg, catalogNames);
        }
        LinkedHashSet<String> appfilterNames = new LinkedHashSet<>();
        collectLauncherIconNames(pm, res, pkg, appfilterNames);
        return toEntries(res, pkg, appfilterNames);
    }

    private static List<IconPack.IconEntry> toEntries(
            Resources res, String pkg, LinkedHashSet<String> names) {
        List<IconPack.IconEntry> entries = new ArrayList<>(names.size());
        for (String name : names) {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id != 0) {
                entries.add(new IconPack.IconEntry(name, id));
            }
        }
        return entries;
    }

    private static void collectLauncherIconNames(PackageManager pm, Resources res, String pkg,
            LinkedHashSet<String> out) throws IOException, XmlPullParserException {
        int resId = res.getIdentifier("appfilter", "xml", pkg);
        if (resId == 0) return;

        Set<String> launchable = new HashSet<>();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(launcherIntent, 0)) {
            launchable.add(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .flattenToString());
        }

        XmlResourceParser parseXml = pm.getXml(pkg, resId, null);
        while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
            if (parseXml.getEventType() != XmlPullParser.START_TAG
                    || !"item".equals(parseXml.getName())) {
                continue;
            }
            String drawable = parseXml.getAttributeValue(null, "drawable");
            String component = parseXml.getAttributeValue(null, "component");
            if (drawable == null || component == null) continue;
            ComponentName cn = parseComponent(component);
            if (cn == null || !launchable.contains(cn.flattenToString())) continue;
            out.add(drawable);
        }
    }

    private static void collectDrawableNames(PackageManager pm, Resources res, String pkg,
            String xmlResName, LinkedHashSet<String> out) throws IOException, XmlPullParserException {
        int resId = res.getIdentifier(xmlResName, "xml", pkg);
        if (resId == 0) return;
        XmlResourceParser parseXml = pm.getXml(pkg, resId, null);
        while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
            if (parseXml.getEventType() == XmlPullParser.START_TAG
                    && "item".equals(parseXml.getName())) {
                String drawable = parseXml.getAttributeValue(null, "drawable");
                if (drawable != null) {
                    out.add(drawable);
                }
            }
        }
    }

    private static void addItem(XmlResourceParser parseXml,
                                IconPack.Data iconPack) {
        String component = parseXml.getAttributeValue(null, "component");
        String drawable = parseXml.getAttributeValue(null, "drawable");
        if (component != null && drawable != null) {
            ComponentName componentName = parseComponent(component);
            if (componentName != null) {
                iconPack.drawables.put(componentName, drawable);
            }
        }
    }

    private static void addCalendar(XmlResourceParser parseXml, IconPack.Data iconPack) {
        String component = parseXml.getAttributeValue(null, "component");
        String prefix = parseXml.getAttributeValue(null, "prefix");
        if (component != null && prefix != null) {
            ComponentName componentName = parseComponent(component);
            if (componentName != null) {
                iconPack.calendarPrefix.put(componentName, prefix);
            }
        }
    }

    private static ComponentName parseComponent(String component) {
        if (component.startsWith("ComponentInfo{") && component.endsWith("}")) {
            component = component.substring(14, component.length() - 1);
            return ComponentName.unflattenFromString(component);
        }
        return null;
    }

    private static void addImgsTo(Resources res, String pkg, XmlResourceParser parseXml,
                                  List<Integer> list) {
        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
            if (parseXml.getAttributeName(i).startsWith("img")) {
                int id = res.getIdentifier(parseXml.getAttributeValue(i), "drawable", pkg);
                if (id != 0) {
                    list.add(id);
                }
            }
        }
    }

    private static void setScale(XmlResourceParser parseXml, IconPack.Data iconPack) {
        String factor = parseXml.getAttributeValue(null, "factor");
        if (factor != null) {
            try {
                // ToDo: Parse reference to dimens
                iconPack.scale = Float.parseFloat(factor);
            } catch (NumberFormatException e) {
                Log.d(TAG, "Invalid scale: " + factor, e);
            }
        }
    }

    private static void addClock(Resources res, String pkg, XmlResourceParser parseXml,
                                 IconPack.Data iconPack) {
        String drawable = parseXml.getAttributeValue(null, "drawable");
        if (drawable != null) {
            int drawableId = res.getIdentifier(drawable, "drawable", pkg);
            if (drawableId != 0) {
                iconPack.clockMetadata.put(drawableId, new IconPack.Clock(
                        parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
                        parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
                        parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
                        parseXml.getAttributeIntValue(null, "defaultHour", 0),
                        parseXml.getAttributeIntValue(null, "defaultMinute", 0),
                        parseXml.getAttributeIntValue(null, "defaultSecond", 0)));
            }
        }
    }
}
