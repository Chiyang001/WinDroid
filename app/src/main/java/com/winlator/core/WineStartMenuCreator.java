package com.winlator.core;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import com.winlator.container.Container;
import com.winlator.win32.MSLink;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class WineStartMenuCreator {
    private static int parseShowCommand(String value) {
        if (value.equals("SW_SHOWMAXIMIZED")) {
            return MSLink.SW_SHOWMAXIMIZED;
        }
        else if (value.equals("SW_SHOWMINNOACTIVE")) {
            return MSLink.SW_SHOWMINNOACTIVE;
        }
        else return MSLink.SW_SHOWNORMAL;
    }

    private static void createMenuEntry(JSONObject item, File currentDir, Set<File> builtInLinks) throws JSONException {
        if (item.has("children")) {
            currentDir = new File(currentDir, item.getString("name"));
            currentDir.mkdirs();

            JSONArray children = item.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) createMenuEntry(children.getJSONObject(i), currentDir, builtInLinks);
        }
        else {
            File outputFile = new File(currentDir, item.getString("name")+".lnk");
            MSLink.LinkInfo linkInfo = new MSLink.LinkInfo();
            linkInfo.targetPath = item.getString("path");
            linkInfo.arguments = item.optString("cmdArgs");
            linkInfo.iconLocation = item.optString("iconLocation", linkInfo.targetPath);
            linkInfo.iconIndex = item.optInt("iconIndex", 0);
            if (item.has("showCommand")) linkInfo.showCommand = parseShowCommand(item.getString("showCommand"));
            if (MSLink.createFile(linkInfo, outputFile)) builtInLinks.add(outputFile);
        }
    }

    private static void removeMenuEntry(JSONObject item, File currentDir) throws JSONException {
        if (item.has("children")) {
            currentDir = new File(currentDir, item.getString("name"));

            JSONArray children = item.getJSONArray("children");
            for (int i = 0; i < children.length(); i++) removeMenuEntry(children.getJSONObject(i), currentDir);

            if (FileUtils.isEmpty(currentDir)) currentDir.delete();
        }
        else (new File(currentDir, item.getString("name")+".lnk")).delete();
    }

    private static void removeOldMenu(File containerStartMenuFile, File startMenuDir) {
        if (!containerStartMenuFile.isFile()) return;
        try {
            JSONArray data = new JSONArray(FileUtils.readString(containerStartMenuFile));
            for (int i = 0; i < data.length(); i++) removeMenuEntry(data.getJSONObject(i), startMenuDir);
        }
        catch (JSONException e) {
            containerStartMenuFile.delete();
        }
    }

    private static boolean installChineseFileManager(Context context, Container container) {
        File file = new File(container.getRootDir(), ".wine/drive_c/windows/wfm-zh.exe");
        byte[] program = FileUtils.read(context, "windroid/wfm-zh.exe");
        if (program == null || program.length == 0) return false;
        if (file.length() == program.length && Arrays.equals(FileUtils.read(file), program)) return true;
        AtomicFile target = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = target.startWrite();
            output.write(program);
            target.finishWrite(output);
            return true;
        }
        catch (IOException error) {
            if (output != null) target.failWrite(output);
            Log.w("WinDroid", "Unable to install the Chinese file manager", error);
            return false;
        }
    }

    private static void translateEntries(JSONArray entries, boolean localizedFileManager) throws JSONException {
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            if (item.has("nameZh")) item.put("name", item.getString("nameZh"));
            if (localizedFileManager && item.has("pathZh")) item.put("path", item.getString("pathZh"));
            if (item.has("children")) translateEntries(item.getJSONArray("children"), localizedFileManager);
        }
    }

    public static Set<File> create(Context context, Container container, boolean chinese) {
        Set<File> builtInLinks = new HashSet<>();
        File startMenuDir = container.getStartMenuDir();
        File containerStartMenuFile = new File(container.getRootDir(), ".startmenu");
        removeOldMenu(containerStartMenuFile, startMenuDir);

        try {
            JSONArray data = new JSONArray(FileUtils.readString(context, "wine_startmenu.json"));
            if (chinese) translateEntries(data, installChineseFileManager(context, container));
            FileUtils.writeString(containerStartMenuFile, data.toString());
            for (int i = 0; i < data.length(); i++) createMenuEntry(data.getJSONObject(i), startMenuDir, builtInLinks);
        }
        catch (JSONException e) {}
        return builtInLinks;
    }
}
