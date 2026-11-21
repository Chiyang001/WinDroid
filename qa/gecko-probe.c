#define COBJMACROS
#include <windows.h>
#include <ole2.h>
#include <mshtml.h>
#include <stdio.h>

static void pump_messages(void) {
    MSG message;
    while (PeekMessageW(&message, NULL, 0, 0, PM_REMOVE)) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
}

int main(void) {
    int result = 1;
    if (FAILED(OleInitialize(NULL))) return result;

    const CLSID html_document = {0x25336920, 0x03f9, 0x11cf, {0x8f, 0xd0, 0x00, 0xaa, 0x00, 0x68, 0x6f, 0x13}};
    IHTMLDocument2 *document = NULL;
    HRESULT hr = CoCreateInstance(&html_document, NULL, CLSCTX_INPROC_SERVER, &IID_IHTMLDocument2, (void **)&document);
    printf("CoCreateInstance=%08lx\n", (unsigned long)hr);
    if (!document) goto done;

    VARIANT value;
    VariantInit(&value);
    V_VT(&value) = VT_BSTR;
    V_BSTR(&value) = SysAllocString(L"<!doctype html><html><body><div id='probe' "
        L"style='position:absolute;left:20px;top:20px;width:137px;height:85px;background:#e83060'>HTML</div>"
        L"</body></html>");
    SAFEARRAY *content = SafeArrayCreateVector(VT_VARIANT, 0, 1);
    LONG first = 0;
    hr = SafeArrayPutElement(content, &first, &value);
    VariantClear(&value);
    if (SUCCEEDED(hr)) hr = IHTMLDocument2_write(document, content);
    SafeArrayDestroy(content);
    if (SUCCEEDED(hr)) hr = IHTMLDocument2_close(document);
    printf("Document write=%08lx\n", (unsigned long)hr);
    if (FAILED(hr)) goto done;

    IHTMLWindow2 *window = NULL;
    hr = IHTMLDocument2_get_parentWindow(document, &window);
    if (SUCCEEDED(hr) && window) {
        BSTR script = SysAllocString(L"document.getElementById('probe').innerText = 'JS ' + (6 * 7);");
        BSTR language = SysAllocString(L"JavaScript");
        hr = IHTMLWindow2_execScript(window, script, language, &value);
        printf("JavaScript=%08lx\n", (unsigned long)hr);
        VariantClear(&value);
        SysFreeString(language);
        SysFreeString(script);
        IHTMLWindow2_Release(window);
    }

    IHTMLDocument3 *document3 = NULL;
    IHTMLElement *element = NULL;
    hr = IHTMLDocument2_QueryInterface(document, &IID_IHTMLDocument3, (void **)&document3);
    if (SUCCEEDED(hr)) {
        BSTR id = SysAllocString(L"probe");
        hr = IHTMLDocument3_getElementById(document3, id, &element);
        SysFreeString(id);
        IHTMLDocument3_Release(document3);
    }
    if (element) {
        LONG width = 0, height = 0;
        BSTR text = NULL;
        for (unsigned attempt = 0; attempt < 100 && !width; ++attempt) {
            pump_messages();
            IHTMLElement_get_offsetWidth(element, &width);
            IHTMLElement_get_offsetHeight(element, &height);
            if (!width) Sleep(20);
        }
        IHTMLElement_get_innerText(element, &text);
        printf("CSS layout=%ldx%ld DOM text=%ls\n", width, height, text ? text : L"");
        if (text && !wcscmp(text, L"JS 42") && width == 137 && height == 85) result = 0;
        SysFreeString(text);
        IHTMLElement_Release(element);
    }

done:
    if (document) IHTMLDocument2_Release(document);
    OleUninitialize();
    return result;
}
