package backlogs.dinamico.seed;

import backlogs.dinamico.model.config.FlowConfig;
import backlogs.dinamico.repository.config.FlowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Profile("dev")
@Component
@RequiredArgsConstructor
public class FlowConfigSeeder implements CommandLineRunner {

    private final FlowConfigRepository repo;

    @Override
    public void run(String... args) throws Exception {

        ObjectId tenant = new ObjectId("68ed8cdedca3a97d9f999ba9"); // <-- tu tenant demo

        repo.findByTenantIdAndFlowId(tenant, "Mobil").ifPresentOrElse(
                it -> {},
                () -> {
                    var now = Instant.now();
                    var cfg = FlowConfig.builder()
                            .tenantId(tenant)
                            .flowId("mobil")
                            .name("Mobil")
                            .icon("smartphone")
                            .color("#1976d2")
                            .createdAt(now).updateAt(now)
                            .modules(List.of(
                                    FlowConfig.ModuleItem.builder()
                                            .id("dashboard").name("Dashboard").icon("dashboard")
                                            .route("/mobil/dashboard").order(1).active(true)
                                            .perms(List.of("events:read"))
                                            .build(),
                                    FlowConfig.ModuleItem.builder()
                                            .id("eventos").name("Eventos").icon("event")
                                            .route("/mobil/eventos").order(2).active(true)
                                            .perms(List.of("events:read"))
                                            .build(),
                                    FlowConfig.ModuleItem.builder()
                                            .id("errores").name("Errores").icon("error")
                                            .route("/mobil/errores").order(3).active(true)
                                            .perms(List.of("events:read"))
                                            .build()
                            ))
                            .build();
                    repo.save(cfg);
                }
        );
    }

}
