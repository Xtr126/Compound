# Desktop app windows

Compound opens each selected launcher activity in its own remote virtual display. The desktop window bar supports dragging, resizing from the left and right edges, the top and bottom edges, and all four corners, minimizing, maximizing/restoring, and closing. Drag a corner diagonally to change width and height together; the opposite corner's horizontal and vertical edges stay fixed and the window stays within the desktop.

- **Minimize** hides the window and detaches its surface from the virtual display. The display and launched task are kept so restoring the window does not launch another task.
- **Maximize/restore** fills the available desktop area and restores the prior size and position when toggled again. Resizing the surface updates the virtual display dimensions. Normal windows are kept within the desktop when its available size changes.
- **Close** releases the virtual display and removes the window. Repeated close requests are safe.

Virtual display creation, resize, input injection, surface attachment, and release run on a dedicated worker. If the privileged service disconnects, active windows are removed because their virtual displays and task sessions are no longer reliable; launch those apps again after reconnecting. The privileged service provides `setVirtualDisplaySurface(displayId, surface)`, where a null surface detaches rendering while retaining the display/task.

Dark-mode (`uiMode`) changes are handled by the existing Activity so its controller and hosted displays survive. Other Activity destruction still closes sessions. Verify a day/night switch preserves task/display IDs and minimize/restore behavior. The maximize control switches to overlapping-window restore artwork; taskbar entries retain app artwork and add minimized/maximized state icons.

For manual resize checks, drag each corner both outward and inward. Confirm diagonal dragging updates both the hosted surface dimensions and window bounds, the opposite corner remains fixed, the minimum window size is enforced at desktop edges, and pointer icons indicate horizontal, vertical, or diagonal resizing on their corresponding handles.
