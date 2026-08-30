# UI/UX Improvement Plan for OmniControl

This plan aims to improve the remote control screen's usability and aesthetics by refining button sizes, removing redundant containers/paddings, fixing navbar heights, and implementing immersive mode to hide system navigation buttons.

## User Review Required

> [!IMPORTANT]
> Hiding the system navigation bar (Home, Back, Recents buttons) will make the app use the full screen. Users will need to swipe from the edge to see the system bars again. This is common for "remote control" apps to avoid accidental exits.

## Proposed Changes

### [Component] Navigation & System UI

#### [MODIFY] [MainActivity.kt](file:///C:/Users/user/AndroidStudioProjects/OmniControl/app/src/main/java/com/example/omnicontrol/MainActivity.kt)
- Implement Immersive Mode to hide system navigation bars.
- Refine `NavigationBar` height and padding to be more compact and integrated.
- Remove the floating effect (padding/clip) if it feels too "clunky" as requested by "remove containers".

### [Component] Remote Screen

#### [MODIFY] [RemoteScreen.kt](file:///C:/Users/user/AndroidStudioProjects/OmniControl/app/src/main/java/com/example/omnicontrol/ui/screens/RemoteScreen.kt)
- **Top Bar**: Reduce vertical padding and unify the look.
- **Device Selector**: Remove the secondary color background container; use a more subtle approach (just text with an icon or a very thin outline).
- **Button Layout**:
    - Reduce large horizontal paddings (48dp -> 24dp or less).
    - Adjust button sizes: `CircularActionBtn` (60dp -> 54dp), `Power` (56dp -> 54dp), `OK` (72dp -> 64dp).
    - Make the DPad slightly more compact (240dp -> 220dp).
- **Paddings & Spacing**: Reduce `Spacer` heights to fit everything on one screen without scrolling.
- **Visuals**: Remove heavy `Surface` backgrounds where possible to make the UI feel "lighter".

## Verification Plan

### Automated Tests
- Not applicable for pure UI layout changes, but I will ensure the project still builds.

### Manual Verification
- Deploy to device/emulator.
- Check if system navigation bars are hidden.
- Check if the entire remote interface fits on the screen without scrolling.
- Verify that the app's navigation bar is easily accessible and looks good.
