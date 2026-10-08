fn main() {
    // The Android plugin is registered at runtime (`android::init`), so Tauri's ACL needs an
    // inlined manifest to know it exists. Without it the frontend's `addPluginListener`
    // fails with "moga-android.registerListener not allowed. Plugin not found".
    tauri_build::try_build(
        tauri_build::Attributes::new().plugin(
            "moga-android",
            tauri_build::InlinedPlugin::new()
                .commands(&["registerListener", "remove_listener"])
                .default_permission(tauri_build::DefaultPermissionRule::AllowAllCommands),
        ),
    )
    .expect("failed to run tauri-build");
}
