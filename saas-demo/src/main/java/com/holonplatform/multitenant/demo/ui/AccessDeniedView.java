package com.holonplatform.multitenant.demo.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/** Shown when a signed-in user navigates to a view their role does not grant. */
@Route(value = "access-denied", layout = MainLayout.class)
@PageTitle("Access denied")
@PermitAll
public class AccessDeniedView extends VerticalLayout {

    public AccessDeniedView() {
        setPadding(true);
        add(new H2("Access denied"),
                new Paragraph("Your role in this workspace does not grant access to that view."));
    }
}
