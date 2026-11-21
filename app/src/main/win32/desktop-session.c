#include <windows.h>

static LRESULT CALLBACK session_window(HWND window, UINT message, WPARAM wparam, LPARAM lparam)
{
    switch (message)
    {
        case WM_QUERYENDSESSION:
            return TRUE;
        case WM_ENDSESSION:
            if (wparam) PostQuitMessage(0);
            return 0;
        default:
            return DefWindowProcW(window, message, wparam, lparam);
    }
}

void mainCRTStartup(void)
{
    static WNDCLASSW session_class = {
        .lpfnWndProc = session_window,
        .lpszClassName = L"WinDroidDesktopSession"
    };
    MSG message;
    HWND window;
    BOOL result;

    session_class.hInstance = GetModuleHandleW(NULL);
    if (!RegisterClassW(&session_class)) ExitProcess(1);

    // Wine's shutdown utility enumerates top-level windows, including hidden windows.
    window = CreateWindowExW(0, session_class.lpszClassName, L"WinDroid Desktop Session", WS_POPUP,
                             0, 0, 0, 0, NULL, NULL, session_class.hInstance, NULL);
    if (!window) ExitProcess(1);

    while ((result = GetMessageW(&message, NULL, 0, 0)) > 0) DispatchMessageW(&message);
    DestroyWindow(window);
    ExitProcess(result < 0 ? 1 : 0);
}
