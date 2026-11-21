package app.windroid;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.core.KeyValueSet;
import com.winlator.core.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WinDroidRuntimeLibrary {
    static void show(WinDroidActivity activity, WinDroidModel model, Runnable chooseInstaller) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(activity.getString(R.string.wd_runtime_select_packages));
        actions.add(() -> showPackageSelector(activity, model));
        labels.add(activity.getString(R.string.wd_runtime_bundled_components));
        actions.add(() -> showBundledComponents(activity, model));
        labels.add(activity.getString(R.string.wd_runtime_other_installers));
        actions.add(() -> showOtherInstallers(activity, model));
        labels.add(activity.getString(R.string.wd_install_file));
        actions.add(() -> {
            if (canConfigure(activity, model)) chooseInstaller.run();
        });
        dialog(activity).setTitle(R.string.wd_runtime_library)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showPackageSelector(WinDroidActivity activity, WinDroidModel model) {
        if (!canConfigure(activity, model)) return;
        List<WineRuntimePackage> options = new ArrayList<>();
        for (WineRuntimePackage component : WineRuntimePackage.values()) {
            if (component.selectable) options.add(component);
        }
        WineRuntimePackage[] packages = options.toArray(new WineRuntimePackage[0]);
        String[] names = new String[packages.length];
        boolean[] selected = new boolean[packages.length];
        boolean[] installed = new boolean[packages.length];
        for (int i = 0; i < packages.length; i++) {
            names[i] = activity.getString(R.string.wd_runtime_package_label,
                    activity.getString(packages[i].title), packages[i].version);
            installed[i] = model.isRuntimeEnabled(packages[i]);
            selected[i] = installed[i];
        }
        AlertDialog selector = dialog(activity).setTitle(R.string.wd_runtime_select_packages)
                .setMultiChoiceItems(names, selected, (dialog, which, checked) -> {
                    selected[which] = checked;
                    ((AlertDialog)dialog).getButton(AlertDialog.BUTTON_POSITIVE)
                            .setEnabled(!java.util.Arrays.equals(selected, installed));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.wd_runtime_install_selected, (dialog, which) -> {
                    if (!canConfigure(activity, model)) return;
                    Map<WineRuntimePackage, Boolean> changes = new LinkedHashMap<>();
                    for (boolean enable : new boolean[]{false, true}) {
                        for (int i = 0; i < packages.length; i++) {
                            if (selected[i] == enable && selected[i] != installed[i]) changes.put(packages[i], enable);
                        }
                    }
                    model.applyRuntimeChanges(changes);
                }).create();
        selector.setOnShowListener(dialog -> selector.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));
        selector.show();
    }

    private static void showOtherInstallers(WinDroidActivity activity, WinDroidModel model) {
        if (!canConfigure(activity, model)) return;
        List<WineRuntimePackage> packages = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (WineRuntimePackage component : WineRuntimePackage.values()) {
            if (component.selectable) continue;
            packages.add(component);
            labels.add(activity.getString(R.string.wd_runtime_package_label,
                    activity.getString(component.title), component.version));
        }
        dialog(activity).setTitle(R.string.wd_runtime_other_installers)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    WineRuntimePackage component = packages.get(which);
                    if (component == WineRuntimePackage.FRAMEWORK && model.isRuntimeEnabled(WineRuntimePackage.MONO)) {
                        activity.showNotice(activity.getString(R.string.wd_runtime_disable_mono_first));
                        return;
                    }
                    int instructions = component == WineRuntimePackage.DIRECTX ? R.string.wd_runtime_directx_help
                            : R.string.wd_runtime_framework_help;
                    dialog(activity).setTitle(component.title).setMessage(instructions)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.wd_runtime_install_selected, (confirm, button) -> {
                                if (canConfigure(activity, model)) {
                                    model.applyRuntimeChanges(java.util.Collections.singletonMap(component, true));
                                }
                            }).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void showBundledComponents(WinDroidActivity activity, WinDroidModel model) {
        if (!canConfigure(activity, model)) return;
        Container container = model.environment().readContainer();
        if (container == null) {
            activity.showNotice(activity.getString(R.string.wd_error_read_disk));
            return;
        }
        KeyValueSet components = new KeyValueSet(container.getWinComponents());
        List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String[] component : new KeyValueSet(Container.DEFAULT_WINCOMPONENTS)) {
            keys.add(component[0]);
            names.add(StringUtils.getString(activity, component[0]));
        }
        boolean[] selected = new boolean[keys.size()];
        for (int i = 0; i < selected.length; i++) selected[i] = components.getBoolean(keys.get(i));
        dialog(activity).setTitle(R.string.wd_runtime_bundled_components)
                .setMultiChoiceItems(names.toArray(new String[0]), selected, (dialog, which, checked) ->
                        components.put(keys.get(which), checked ? "1" : "0"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!canConfigure(activity, model)) return;
                    // Reload the container in case a Windows session finished while the dialog was open.
                    Container current = model.environment().readContainer();
                    if (current == null) {
                        activity.showNotice(activity.getString(R.string.wd_error_read_disk));
                        return;
                    }
                    current.setWinComponents(components.toString());
                    current.saveData();
                }).show();
    }

    private static boolean canConfigure(WinDroidActivity activity, WinDroidModel model) {
        if (!model.canOpen()) {
            activity.showNotice(activity.getString(R.string.wd_runtime_not_ready));
            return false;
        }
        if (XServerDisplayActivity.hasWinDroidSession()) {
            activity.showNotice(activity.getString(R.string.windroid_runtime_session_active));
            return false;
        }
        return true;
    }

    private static AlertDialog.Builder dialog(WinDroidActivity activity) {
        return new AlertDialog.Builder(activity, R.style.WinDroidDialogTheme);
    }
}
