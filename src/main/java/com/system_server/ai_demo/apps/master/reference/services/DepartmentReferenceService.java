package com.system_server.ai_demo.apps.master.reference.services;

import com.system_server.ai_demo.database.entity.DepartmentsEntity;
import com.system_server.ai_demo.database.mapper.DepartmentsMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 部署参照サービス。部署マスタを参照し、部署名取得・部署存在判定・部署名一覧（部署ID昇順）取得を行う。
 */
@Service
public class DepartmentReferenceService {

    private final DepartmentsMapper departmentsMapper;

    public DepartmentReferenceService(DepartmentsMapper departmentsMapper) {
        this.departmentsMapper = departmentsMapper;
    }

    /**
     * 部署IDに対応する部署名を取得する。
     *
     * @param departmentId 部署ID
     * @return 部署名、該当部署が存在しない場合は {@code null}
     */
    public String getDepartmentName(Integer departmentId) {
        DepartmentsEntity department = departmentsMapper.findByDepartmentId(departmentId);
        return department == null ? null : department.getDepartmentName();
    }

    /**
     * 部署IDが部署マスタに存在するかを判定する。
     *
     * @param departmentId 部署ID
     * @return 存在すれば {@code true}
     */
    public boolean existsDepartment(Integer departmentId) {
        return departmentsMapper.findByDepartmentId(departmentId) != null;
    }

    /**
     * 全ての部署名を部署ID昇順で取得する。
     *
     * @return 部署名の一覧（部署ID昇順）
     */
    public List<String> getDepartmentNames() {
        return departmentsMapper.findAll().stream().map(d -> d.getDepartmentName()).toList();
    }
}
