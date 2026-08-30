# Fix App Bars and Improve UI/UX

The user reported that the top and bottom app bars look "weird" and have incorrect heights. The goal is to standardize the UI, improve visual hierarchy, and ensure all buttons are functional and properly styled.

## User Review Required

- Standardizing the top bar layout across `RemoteScreen`, `DeviceListScreen`, `SetupScreen`, and `SettingsScreen`.
- Adjusting the `NavigationBar` height and padding to fit better within the scaffold.
- Enhancing visual feedback on button interactions (ripple effects, scaling animations).

## Open Questions

- Should I unify the TopAppBar style across all screens or maintain the current slight variations? (I propose unifying for consistency).

## Proposed Changes

### [UI Components]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/user/AndroidStudioProjects/OmniControl/app/src/main/java/com/example/omnicontrol/MainActivity.kt)
- Standardize the `Scaffold` and `NavigationBar` implementation.
- Adjust `NavigationBar` height and internal padding.

#### [MODIFY] [RemoteScreen.kt](file:///C:/Users/user/AndroidStudioProjects/OmniControl/app/src/main/java/com/example/omnicontrol/ui/screens/RemoteScreen.kt)
- Fix top bar layout and height.
- Ensure all command buttons have consistent press feedback.

#### [MODIFY] [DeviceListScreen.kt](file:///C:/Users/user/AndroidStudioProjects/OmniControl/app/src/main/java/com/example/omnicontrol/ui/screens/DeviceListScreen.kt)
- Standardize top bar andFAB layout.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/user/AndroidStudioProjects/OmniControl/app/src/main/java/com/example/omnicontrol/ui/screens/SettingsScreen.kt)
- Clean up top bar layout.

## Verification Plan

### Automated Tests
- Run `app:assembleDebug` to verify no compilation errors.

### Manual Verification
- Check UI on device/emulator to verify app bar heights and button functionality.
- Test button responsiveness.
