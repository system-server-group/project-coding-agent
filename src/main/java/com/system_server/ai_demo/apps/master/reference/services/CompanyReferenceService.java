package com.system_server.ai_demo.apps.master.reference.services;

import com.system_server.ai_demo.database.mapper.CompaniesMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 会社名参照サービス。会社マスタを参照し、取引先オートコンプリート候補となる会社名一覧（会社ID昇順）を提供する。
 */
@Service
public class CompanyReferenceService {

    private final CompaniesMapper companiesMapper;

    public CompanyReferenceService(CompaniesMapper companiesMapper) {
        this.companiesMapper = companiesMapper;
    }

    /**
     * 全ての会社名を会社ID昇順で取得する。
     *
     * @return 会社名の一覧（会社ID昇順）
     */
    public List<String> getCompanyNames() {
        return companiesMapper.findAll().stream().map(c -> c.getCompanyName()).toList();
    }
}
