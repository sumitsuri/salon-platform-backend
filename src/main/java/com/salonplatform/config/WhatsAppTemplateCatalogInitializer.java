package com.salonplatform.config;

import com.salonplatform.whatsapp.WhatsAppTemplateCatalog;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppTemplateCatalogInitializer {

    private final Msg91Properties msg91Properties;

    public WhatsAppTemplateCatalogInitializer(Msg91Properties msg91Properties) {
        this.msg91Properties = msg91Properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        WhatsAppTemplateCatalog.initialize(msg91Properties);
    }
}
