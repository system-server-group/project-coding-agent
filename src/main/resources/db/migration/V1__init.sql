-- 営業情報管理システム 初期スキーマ（テーブル定義書 正本に準拠）
-- 物理名・型・桁・制約名はテーブル定義書どおり。定義書に無い CHECK/INDEX/DEFAULT は付与しない。

-- 会社マスタレコード（company_id は CSV 供給値・自動採番ではない）
CREATE TABLE companies (
    company_id integer NOT NULL,
    company_name varchar(50) NOT NULL,
    CONSTRAINT companies_pkey PRIMARY KEY (company_id)
);

-- 部署マスタレコード（department_id は CSV 供給値・自動採番ではない）
CREATE TABLE departments (
    department_id integer NOT NULL,
    department_name varchar(50) NOT NULL,
    CONSTRAINT departments_pkey PRIMARY KEY (department_id)
);

-- ユーザマスタレコード（department_id は外部キーではない。存在あチェックは参照側）
CREATE TABLE users (
    user_id varchar(60) NOT NULL,
    user_name varchar(30) NOT NULL,
    email varchar(60) NOT NULL,
    department_id integer NOT NULL,
    role varchar(20) NOT NULL,
    password varchar(60) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (user_id)
);

-- 案件情報（management_code は自動採番）
CREATE TABLE projects (
    management_code integer GENERATED ALWAYS AS IDENTITY,
    title varchar(200) NOT NULL,
    status varchar(20) NOT NULL,
    client_name varchar(200),
    commercial_flow varchar(200),
    summary varchar(2000),
    process varchar(200),
    start_date date,
    end_date date,
    skill varchar(200),
    headcount varchar(200),
    remaining_count varchar(200),
    bp_recruitment varchar(20),
    bp_recruitment_detail varchar(200),
    work_location varchar(200),
    estimated_unit_price varchar(200),
    contract_type varchar(20),
    note varchar(4000),
    created_by varchar(30) NOT NULL,
    created_department varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by varchar(30) NOT NULL,
    updated_department varchar(30) NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT projects_pkey PRIMARY KEY (management_code)
);

-- 案件情報添付ファイル（file_id は自動採番、1 案件に 0〜5 件、FK→projects）
CREATE TABLE project_attachments (
    file_id integer GENERATED ALWAYS AS IDENTITY,
    management_code integer NOT NULL,
    file_name varchar(200) NOT NULL,
    file_data bytea NOT NULL,
    file_size integer NOT NULL,
    created_by varchar(30) NOT NULL,
    created_department varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT project_attachments_pkey PRIMARY KEY (file_id),
    CONSTRAINT project_attachments_management_code_fkey
        FOREIGN KEY (management_code) REFERENCES projects (management_code)
);
