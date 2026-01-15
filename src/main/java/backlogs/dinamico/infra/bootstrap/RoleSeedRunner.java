package backlogs.dinamico.infra.bootstrap;

import backlogs.dinamico.repository.core.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import backlogs.dinamico.model.core.PermissionCode;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.RoleCode;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class RoleSeedRunner implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        seed(RoleCode.ORG_OWNER, "Org Owner", "Bootstrap admin con gobierno total",
                true, false,
                EnumSet.of(
                        PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT, PermissionCode.LOG_REPORTS,
                        PermissionCode.ALERTS_MANAGE, PermissionCode.SAVED_VIEWS_MANAGE,
                        PermissionCode.USERS_MANAGE, PermissionCode.ROLES_ASSIGN,
                        PermissionCode.SETTINGS_MANAGE,
                        PermissionCode.VIEW_PII, PermissionCode.VIEW_RAW_PAYLOAD,
                        PermissionCode.CROSS_SYSTEM_CORRELATE
                ));

        seed(RoleCode.ORG_ADMIN, "Org Admin", "Admin operativo sin gobierno de usuarios",
                true, false,
                EnumSet.of(
                        PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT, PermissionCode.LOG_REPORTS,
                        PermissionCode.ALERTS_MANAGE, PermissionCode.SAVED_VIEWS_MANAGE,
                        PermissionCode.VIEW_PII, PermissionCode.VIEW_RAW_PAYLOAD,
                        PermissionCode.CROSS_SYSTEM_CORRELATE
                ));

        seed(RoleCode.SYSTEM_MANAGER, "System Manager", "Jefe de system (scope por systems)",
                false, true,
                EnumSet.of(
                        PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT, PermissionCode.LOG_REPORTS,
                        PermissionCode.ALERTS_MANAGE, PermissionCode.SAVED_VIEWS_MANAGE
                ));

        seed(RoleCode.AUDITOR, "Auditor", "Solo lectura + export",
                true, false,
                EnumSet.of(
                        PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT, PermissionCode.LOG_REPORTS
                ));

        seed(RoleCode.SUPPORT_TI, "Support TI", "Soporte técnico de plataforma",
                true, false,
                EnumSet.of(
                        PermissionCode.LOG_READ,
                        PermissionCode.VIEW_RAW_PAYLOAD
                ));

        seed(RoleCode.VIEWER, "Viewer", "Solo lectura (scope por systems)",
                false, true,
                EnumSet.of(PermissionCode.LOG_READ));

    }

    private void seed(RoleCode code, String name, String desc, boolean orgWide, boolean systemScoped, EnumSet<PermissionCode> perms) {
        if (roleRepository.existsByCode(code)) return;
        roleRepository.save(new Role(code, name, desc, orgWide, systemScoped, perms));
    }
}
