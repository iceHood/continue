package com.continuedev.eclipse.continue.ui;

import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.browser.Browser;
import org.eclipse.ui.part.ViewPart;

import com.continuedev.eclipse.continue.Activator;
import com.continuedev.eclipse.continue.service.ContinuePluginService;
import com.continuedev.eclipse.continue.util.Log;

public class ContinueView extends ViewPart {
    public static final String VIEW_ID = "com.continuedev.eclipse.continue.ui.view";

    private Browser browser;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());
        browser = new Browser(parent, SWT.NONE);

        ContinuePluginService service = Activator.getDefault().getContinueService();
        if (service == null) {
            MessageDialog.openError(parent.getShell(), "Continue", "Continue service is not available.");
            return;
        }

        service.registerBrowser(browser);
        try {
            browser.setUrl(service.resolveGuiUrl());
        } catch (IOException e) {
            Log.error("Failed to load Continue GUI", e);
            MessageDialog.openError(parent.getShell(), "Continue", "Unable to load Continue GUI assets. Check the plugin README for build instructions.");
        }
    }

    @Override
    public void setFocus() {
        if (browser != null && !browser.isDisposed()) {
            browser.setFocus();
        }
    }
}
