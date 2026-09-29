package com.system_server.ai_demo.config;

import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.DepartmentsMapper;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import com.system_server.ai_demo.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * dev プロファイル限定のログイン用初期データ投入。
 *
 * <p>
 * dev では DB を使い捨て扱いとし（{@link FlywayDevConfig} がスキーマ変更時に自動 clean→再マイグレートする）、
 * 起動毎に本クラスがログイン確認用のユーザ／部署を冪等に投入する。これにより自己修復で DB が作り直されても 起動後に再び投入され、開発者は常に同じアカウントでログインできる。
 *
 * <p>
 * 投入値は開発者に見られて問題ない前提の dev 専用値（application-dev.yml と同方針）。stg/prd では本 Bean を
 * 構成しない（{@code @Profile("dev")}）。テストは {@code test} プロファイルで動かすため本 Bean は読み込まれない。
 */
@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    /** ログイン確認用アカウントのユーザID。 */
    private static final String ADMIN_USER_ID = "admin";

    /** ログイン確認用アカウントの平文パスワード（dev 専用）。 */
    private static final String ADMIN_RAW_PASSWORD = "password";

    /** ログイン確認用アカウントの所属部署ID。 */
    private static final int DEV_DEPARTMENT_ID = 1;

    private final DepartmentsMapper departmentsMapper;
    private final UsersMapper usersMapper;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(DepartmentsMapper departmentsMapper, UsersMapper usersMapper,
            PasswordEncoder passwordEncoder) {
        this.departmentsMapper = departmentsMapper;
        this.usersMapper = usersMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDevDepartment();
        seedAdminUser();
    }

    /** ログイン用ユーザの所属部署を、未存在の場合のみ投入する。 */
    private void seedDevDepartment() {
        if (departmentsMapper.findByDepartmentId(DEV_DEPARTMENT_ID) != null) {
            return;
        }
        DepartmentsEntity department = new DepartmentsEntity();
        department.setDepartmentId(DEV_DEPARTMENT_ID);
        department.setDepartmentName("開発部");
        departmentsMapper.insert(department);
        log.info("dev seed: 部署を投入しました (department_id={})", DEV_DEPARTMENT_ID);
    }

    /** ログイン確認用のシステム管理者ユーザを、未存在の場合のみ投入する。 */
    private void seedAdminUser() {
        if (usersMapper.findByUserId(ADMIN_USER_ID) != null) {
            return;
        }
        UsersEntity user = new UsersEntity();
        user.setUserId(ADMIN_USER_ID);
        user.setUserName("開発管理者");
        user.setEmail("admin@example.com");
        user.setDepartmentId(DEV_DEPARTMENT_ID);
        user.setRole(Role.SYSTEM_ADMIN.getCode());
        user.setPassword(passwordEncoder.encode(ADMIN_RAW_PASSWORD));
        usersMapper.insert(user);
        log.info("dev seed: ログインユーザを投入しました (userId={})", ADMIN_USER_ID);
    }
}
