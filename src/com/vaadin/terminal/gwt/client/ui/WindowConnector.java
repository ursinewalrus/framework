/*
@VaadinApache2LicenseForJavaFiles@
 */
package com.vaadin.terminal.gwt.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.DomEvent.Type;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.ComponentConnector;
import com.vaadin.terminal.gwt.client.LayoutManager;
import com.vaadin.terminal.gwt.client.UIDL;
import com.vaadin.terminal.gwt.client.ui.ShortcutActionHandler.BeforeShortcutActionListener;

public class WindowConnector extends AbstractComponentContainerConnector
        implements BeforeShortcutActionListener, SimpleManagedLayout,
        PostLayoutListener {

    private static final String CLICK_EVENT_IDENTIFIER = PanelConnector.CLICK_EVENT_IDENTIFIER;

    private ClickEventHandler clickEventHandler = new ClickEventHandler(this,
            CLICK_EVENT_IDENTIFIER) {

        @Override
        protected <H extends EventHandler> HandlerRegistration registerHandler(
                H handler, Type<H> type) {
            return getWidget().addDomHandler(handler, type);
        }
    };

    @Override
    protected boolean delegateCaptionHandling() {
        return false;
    };

    @Override
    protected void init() {
        super.init();
        getLayoutManager().registerDependency(this,
                getWidget().contentPanel.getElement());
    }

    @Override
    public void updateFromUIDL(UIDL uidl, ApplicationConnection client) {
        getWidget().id = getId();
        getWidget().client = client;

        // Workaround needed for Testing Tools (GWT generates window DOM
        // slightly different in different browsers).
        DOM.setElementProperty(getWidget().closeBox, "id", getId()
                + "_window_close");

        if (uidl.hasAttribute("invisible")) {
            getWidget().hide();
            return;
        }

        if (isRealUpdate(uidl)) {
            if (uidl.getBooleanAttribute("modal") != getWidget().vaadinModality) {
                getWidget().setVaadinModality(!getWidget().vaadinModality);
            }
            if (!getWidget().isAttached()) {
                getWidget().setVisible(false); // hide until
                                               // possible centering
                getWidget().show();
            }
            if (uidl.getBooleanAttribute("resizable") != getWidget().resizable) {
                getWidget().setResizable(!getWidget().resizable);
            }
            getWidget().resizeLazy = uidl.hasAttribute(VView.RESIZE_LAZY);

            getWidget().setDraggable(!uidl.hasAttribute("fixedposition"));

            // Caption must be set before required header size is measured. If
            // the caption attribute is missing the caption should be cleared.
            getWidget()
                    .setCaption(
                            getState().getCaption(),
                            uidl.getStringAttribute(AbstractComponentConnector.ATTRIBUTE_ICON));
        }

        getWidget().visibilityChangesDisabled = true;
        super.updateFromUIDL(uidl, client);
        if (!isRealUpdate(uidl)) {
            return;
        }
        getWidget().visibilityChangesDisabled = false;

        clickEventHandler.handleEventHandlerRegistration(client);

        getWidget().immediate = getState().isImmediate();

        getWidget().setClosable(!getState().isReadOnly());

        // Initialize the position form UIDL
        int positionx = uidl.getIntVariable("positionx");
        int positiony = uidl.getIntVariable("positiony");
        if (positionx >= 0 || positiony >= 0) {
            if (positionx < 0) {
                positionx = 0;
            }
            if (positiony < 0) {
                positiony = 0;
            }
            getWidget().setPopupPosition(positionx, positiony);
        }

        boolean showingUrl = false;
        int childIndex = 0;
        UIDL childUidl = uidl.getChildUIDL(childIndex++);
        while ("open".equals(childUidl.getTag())) {
            // TODO multiple opens with the same target will in practice just
            // open the last one - should we fix that somehow?
            final String parsedUri = client.translateVaadinUri(childUidl
                    .getStringAttribute("src"));
            if (!childUidl.hasAttribute("name")) {
                final Frame frame = new Frame();
                DOM.setStyleAttribute(frame.getElement(), "width", "100%");
                DOM.setStyleAttribute(frame.getElement(), "height", "100%");
                DOM.setStyleAttribute(frame.getElement(), "border", "0px");
                frame.setUrl(parsedUri);
                getWidget().contentPanel.setWidget(frame);
                showingUrl = true;
            } else {
                final String target = childUidl.getStringAttribute("name");
                Window.open(parsedUri, target, "");
            }
            childUidl = uidl.getChildUIDL(childIndex++);
        }

        final ComponentConnector lo = client.getPaintable(childUidl);
        if (getWidget().layout != null) {
            if (getWidget().layout != lo) {
                // remove old
                client.unregisterPaintable(getWidget().layout);
                getWidget().contentPanel.remove(getWidget().layout.getWidget());
                // add new
                if (!showingUrl) {
                    getWidget().contentPanel.setWidget(lo.getWidget());
                }
                getWidget().layout = lo;
            }
        } else if (!showingUrl) {
            getWidget().contentPanel.setWidget(lo.getWidget());
            getWidget().layout = lo;
        }

        getWidget().layout.updateFromUIDL(childUidl, client);

        // we may have actions and notifications
        if (uidl.getChildCount() > 1) {
            final int cnt = uidl.getChildCount();
            for (int i = 1; i < cnt; i++) {
                childUidl = uidl.getChildUIDL(i);
                if (childUidl.getTag().equals("actions")) {
                    if (getWidget().shortcutHandler == null) {
                        getWidget().shortcutHandler = new ShortcutActionHandler(
                                getId(), client);
                    }
                    getWidget().shortcutHandler.updateActionMap(childUidl);
                }
            }

        }

        // setting scrollposition must happen after children is rendered
        getWidget().contentPanel.setScrollPosition(uidl
                .getIntVariable("scrollTop"));
        getWidget().contentPanel.setHorizontalScrollPosition(uidl
                .getIntVariable("scrollLeft"));

        // Center this window on screen if requested
        // This had to be here because we might not know the content size before
        // everything is painted into the window
        if (uidl.getBooleanAttribute("center")) {
            // mark as centered - this is unset on move/resize
            getWidget().centered = true;
        } else {
            // don't try to center the window anymore
            getWidget().centered = false;
        }
        getWidget().setVisible(true);

        // ensure window is not larger than browser window
        if (getWidget().getOffsetWidth() > Window.getClientWidth()) {
            getWidget().setWidth(Window.getClientWidth() + "px");
        }
        if (getWidget().getOffsetHeight() > Window.getClientHeight()) {
            getWidget().setHeight(Window.getClientHeight() + "px");
        }

        client.getView().getWidget().scrollIntoView(uidl);

        if (uidl.hasAttribute("bringToFront")) {
            /*
             * Focus as a side-efect. Will be overridden by
             * ApplicationConnection if another component was focused by the
             * server side.
             */
            getWidget().contentPanel.focus();
            getWidget().bringToFrontSequence = uidl
                    .getIntAttribute("bringToFront");
            VWindow.deferOrdering();
        }
    }

    public void updateCaption(ComponentConnector component, UIDL uidl) {
        // NOP, window has own caption, layout captio not rendered
    }

    public void onBeforeShortcutAction(Event e) {
        // NOP, nothing to update just avoid workaround ( causes excess
        // blur/focus )
    }

    @Override
    public VWindow getWidget() {
        return (VWindow) super.getWidget();
    }

    @Override
    protected Widget createWidget() {
        return GWT.create(VWindow.class);
    }

    public void layout() {
        LayoutManager lm = getLayoutManager();
        VWindow window = getWidget();
        Element contentElement = window.contentPanel.getElement();
        if (!window.layout.isUndefinedWidth()
                && lm.getOuterWidth(contentElement) < VWindow.MIN_CONTENT_AREA_WIDTH) {
            // Use minimum width if less than a certain size
            window.setWidth(VWindow.MIN_CONTENT_AREA_WIDTH + "px");
        }

        if (!window.layout.isUndefinedHeight()
                && lm.getOuterHeight(contentElement) < VWindow.MIN_CONTENT_AREA_HEIGHT) {
            // Use minimum height if less than a certain size
            window.setHeight(VWindow.MIN_CONTENT_AREA_HEIGHT + "px");
        }

    }

    public void postLayout() {
        VWindow window = getWidget();
        if (window.centered) {
            window.center();
        }
        window.updateShadowSizeAndPosition();
    }

}
