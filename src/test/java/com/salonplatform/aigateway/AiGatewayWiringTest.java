package com.salonplatform.aigateway;

import com.salonplatform.aigateway.config.AiGatewayConfiguration;
import com.salonplatform.aigateway.ledger.AuditEvent;
import com.salonplatform.aigateway.ledger.GatewayLedger;
import com.salonplatform.aigateway.ledger.InMemoryGatewayLedger;
import com.salonplatform.aigateway.model.GatewayRequest;
import com.salonplatform.aigateway.model.Message;
import com.salonplatform.aigateway.model.Subject;
import com.salonplatform.aigateway.policy.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The gateway builds from whatever an application provides, and works with nothing but a profile. */
class AiGatewayWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AiGatewayConfiguration.class);

    private static GatewayRequest request(String text) {
        return new GatewayRequest("test", "p", new Subject("t", "u", "r"), "x", "claude-sonnet-5-5", List.of(Message.user(text)), Set.of());
    }

    @Test
    void withOnlyAProfileItStartsAndProtectsAgainstSecrets() {
        runner.withBean(Profile.class, () -> Profile.protectionOnly("p")).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(AiGateway.class);
            AiGateway gateway = ctx.getBean(AiGateway.class);
            assertThat(gateway.inspectInbound(request("password: Abc12345")).kind()).isEqualTo(com.salonplatform.aigateway.model.InboundDecision.Kind.BLOCK);
            assertThat(gateway.inspectInbound(request("hello there")).allowed()).isTrue();
        });
    }

    @Test
    void aLedgerProvidedByTheApplicationIsTheOneUsed() {
        InMemoryGatewayLedger mine = new InMemoryGatewayLedger();
        runner.withBean(Profile.class, () -> Profile.protectionOnly("p")).withBean(GatewayLedger.class, () -> mine).run(ctx -> {
            ctx.getBean(AiGateway.class).inspectInbound(request("hello there"));
            assertThat(mine.events()).extracting(AuditEvent::kind).contains(AuditEvent.REQUEST);
        });
    }

    @Test
    void withNoProfilesAtAllItStillStartsInProtectionOnlyMode() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(AiGateway.class));
    }
}
