package com.system_server.ai_demo.config;

import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import com.system_server.ai_demo.database.entity.UsersEntity;
import com.system_server.ai_demo.database.mapper.DepartmentsMapper;
import com.system_server.ai_demo.database.mapper.UsersMapper;
import com.system_server.ai_demo.enums.Role;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * dev プロファイル限定の初期データ投入（ログイン用のユーザ／部署と、取引先候補となる会社マスタ）。
 *
 * <p>
 * dev では DB を使い捨て扱いとし（{@link FlywayDevConfig} がスキーマ変更時に自動 clean→再マイグレートする）、
 * 起動毎に本クラスがログイン確認用のユーザ／部署と会社マスタを冪等に投入する。これにより自己修復で DB が作り直されても
 * 起動後に再び投入され、開発者は常に同じアカウントでログインでき、案件情報の取引先オートコンプリートに候補が現れる。
 *
 * <p>
 * 会社マスタは件数の確認（0 件判定）と一括投入を JDBC で直接行う（会社マスタの Mapper は件数取得・一括登録のメソッドを持たないため）。
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

    /** 取引先オートコンプリート確認用の会社マスタ（会社ID・会社名）。 */
    private static final List<Object[]> DEV_COMPANIES = List.of(new Object[] {1, "株式会社アルファ"},
            new Object[] {2, "株式会社ブラボー"}, new Object[] {3, "株式会社チャーリー"},
            new Object[] {4, "株式会社デルタ"}, new Object[] {5, "株式会社エコー"}, new Object[] {6, "株式会社フォックス"},
            new Object[] {7, "株式会社ゴルフ"}, new Object[] {8, "株式会社ホテル"}, new Object[] {9, "株式会社インディア"},
            new Object[] {10, "株式会社ジュリエット"}, new Object[] {11, "株式会社キロ"},
            new Object[] {12, "株式会社リマ"});

    private final DepartmentsMapper departmentsMapper;
    private final UsersMapper usersMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DevDataInitializer(DepartmentsMapper departmentsMapper, UsersMapper usersMapper,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.departmentsMapper = departmentsMapper;
        this.usersMapper = usersMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDevDepartment();
        seedAdminUser();
        seedDevCompanies();
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

    /** 取引先オートコンプリート確認用の会社マスタを、会社マスタが 0 件の場合のみ JDBC で投入する。 */
    private void seedDevCompanies() {
        Integer count =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.batchUpdate("INSERT INTO companies (company_id, company_name) VALUES (?, ?)",
                DEV_COMPANIES);
        log.info("dev seed: 会社マスタを投入しました ({} 件)", DEV_COMPANIES.size());
    }
}
